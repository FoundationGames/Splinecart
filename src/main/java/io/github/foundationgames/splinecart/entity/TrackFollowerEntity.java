package io.github.foundationgames.splinecart.entity;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.util.Pose;
import io.github.foundationgames.splinecart.util.SUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public class TrackFollowerEntity extends Entity {
    public static final double FRICTION = 0.997;
    public static final double CHAIN_DRIVE_SPEED = 0.36;
    public static final double MAGNETIC_SPEED_FACTOR = 1.6;
    public static final double MAGNETIC_ACCEL = 0.07;

    private static final double GRAVITY = 0.04;

    private @Nullable BlockPos startTie;
    private @Nullable BlockPos endTie;
    private double splinePieceProgress = 0; // t
    private double motionScale; // t-distance per block
    private double trackVelocity;

    private final Vector3d serverPosition = new Vector3d();
    private final Vector3d serverVelocity = new Vector3d();
    private int positionInterpSteps;
    private int oriInterpSteps;

    private static final TrackedData<Quaternionf> ORIENTATION = DataTracker.registerData(TrackFollowerEntity.class, TrackedDataHandlerRegistry.QUATERNION_F);
    private final Matrix3d basis = new Matrix3d().identity();

    private final Quaternionf lastClientOrientation = new Quaternionf();
    private final Quaternionf clientOrientation = new Quaternionf();

    private boolean hadPassenger = false;

    private boolean firstPositionUpdate = true;
    private boolean firstOriUpdate = true;

    private Vec3d clientMotion = Vec3d.ZERO;

    public TrackFollowerEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    public TrackFollowerEntity(World world) {
        this(Splinecart.TRACK_FOLLOWER, world);
    }

    public static @Nullable TrackFollowerEntity create(World world, Vec3d startPos, BlockPos tie, Vec3d velocity) {
        var tieE = TrackTiesBlockEntity.of(world, tie);
        double trackVelocity, progress;
        BlockPos start, end;
        if (tieE != null) {
            var tieDir = new Vector3d(0, 0, 1).mul(tieE.pose().basis()).normalize();
            var velDir = new Vector3d(velocity.getX(), velocity.getY(), velocity.getZ()).normalize();

            if (tieDir.dot(velDir) >= 0) { // Heading in positive direction
                trackVelocity = velocity.length();
                start = tie;
                end = tieE.nextPos();
                progress = 0;
            } else {
                trackVelocity = -velocity.length();
                start = tieE.prevPos();
                end = tie;
                progress = 1;
            }
        } else {
            return null;
        }

        var startE = TrackTiesBlockEntity.of(world, start);
        if (startE != null) {
            var follower = new TrackFollowerEntity(world);
            follower.trackVelocity = trackVelocity;
            follower.splinePieceProgress = progress;
            follower.setStretch(start, end);
            follower.setPosition(startPos);
            follower.getDataTracker().set(ORIENTATION, startE.pose().basis().getNormalizedRotation(new Quaternionf()));

            return follower;
        }

        return null;
    }

    public void setStretch(@Nullable BlockPos start, @Nullable BlockPos end) {
        this.startTie = start;
        this.endTie = end;
    }

    // For more accurate client side position interpolation, we can conveniently use the
    // same cubic hermite spline formula rather than linear interpolation like vanilla,
    // since we have not only the position but also its derivative (velocity)
    protected void interpPos(int step) {
        double t = 1 / (double)step;

        var clientPos = new Vector3d(this.getX(), this.getY(), this.getZ());

        var cv = this.getVelocity();
        var clientVel = new Vector3d(cv.getX(), cv.getY(), cv.getZ());

        var newClientPos = new Vector3d();
        var newClientVel = new Vector3d();
        Pose.cubicHermiteSpline(t, 1, clientPos, clientVel, this.serverPosition, this.serverVelocity,
                newClientPos, newClientVel);

        this.setPosition(newClientPos.x(), newClientPos.y(), newClientPos.z());
        this.setVelocity(newClientVel.x(), newClientVel.y(), newClientVel.z());
    }

    @Override
    public void tick() {
        super.tick();

        var world = this.getWorld();
        if (world.isClient()) {
            this.clientMotion = this.getPos().negate();
            if (this.positionInterpSteps > 0) {
                this.interpPos(this.positionInterpSteps);
                this.positionInterpSteps--;
            } else {
                this.refreshPosition();
                this.setVelocity(this.serverVelocity.x(), this.serverVelocity.y(), this.serverVelocity.z());
            }
            this.clientMotion = this.clientMotion.add(this.getPos());

            this.lastClientOrientation.set(this.clientOrientation);
            if (this.oriInterpSteps > 0) {
                float delta = 1 / (float) oriInterpSteps;
                this.clientOrientation.slerp(this.getDataTracker().get(ORIENTATION), delta);
                this.oriInterpSteps--;
            } else {
                this.clientOrientation.set(this.getDataTracker().get(ORIENTATION));
            }
        } else {
            this.updateServer();
        }
    }

    public void getClientOrientation(Quaternionf q, float tickDelta) {
        this.lastClientOrientation.slerp(this.clientOrientation, tickDelta, q);
    }

    public Vec3d getClientMotion() {
        return this.clientMotion;
    }

    public Matrix3dc getServerBasis() {
        return this.basis;
    }

    public void destroy() {
        this.remove(RemovalReason.KILLED);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    private void flyOffTrack(Entity firstPassenger) {
        firstPassenger.stopRiding();

        var newVel = new Vector3d(0, 0, this.trackVelocity).mul(this.basis);
        firstPassenger.setVelocity(newVel.x(), newVel.y(), newVel.z());
        this.destroy();
    }

    protected void updateServer() {
        for (var passenger : this.getPassengerList()) {
            passenger.fallDistance = 0;
        }

        var passenger = this.getFirstPassenger();
        if (passenger != null) {
            if (!hadPassenger) {
                hadPassenger = true;
            } else {
                var world = this.getWorld();
                var startE = TrackTiesBlockEntity.of(world, this.startTie);
                var endE = TrackTiesBlockEntity.of(world, this.endTie);
                if (startE == null || endE == null) {
                    this.destroy();
                    return;
                }

                this.splinePieceProgress += this.trackVelocity * this.motionScale;
                if (this.splinePieceProgress > 1) {
                    this.splinePieceProgress -= 1;

                    var nextE = endE.next();
                    if (nextE == null) {
                        this.flyOffTrack(passenger);
                        return;
                    } else {
                        this.setStretch(this.endTie, nextE.getPos());
                        startE = endE;
                        endE = nextE;
                    }
                } else if (this.splinePieceProgress < 0) {
                    this.splinePieceProgress += 1;

                    var prevE = startE.prev();
                    if (prevE == null) {
                        this.flyOffTrack(passenger);
                        return;
                    } else {
                        this.setStretch(prevE.getPos(), this.startTie);
                        endE = startE;
                        startE = prevE;
                    }
                }

                var pos = new Vector3d();
                var grad = new Vector3d(); // Change in position per change in spline progress
                startE.pose().interpolate(endE.pose(), this.splinePieceProgress, pos, this.basis, grad);

                this.setPosition(pos.x(), pos.y(), pos.z());
                this.getDataTracker().set(ORIENTATION, this.basis.getNormalizedRotation(new Quaternionf()));

                double gradLen = grad.length();
                if (gradLen != 0) {
                    this.motionScale = 1 / grad.length();
                }

                var ngrad = new Vector3d(grad).normalize();
                var gravity = -ngrad.y() * GRAVITY;

                double dt = this.trackVelocity * this.motionScale; // Change in spline progress per tick
                grad.mul(dt); // Change in position per tick (velocity)
                this.setVelocity(grad.x(), grad.y(), grad.z());

                var passengerVel = passenger.getVelocity();
                var push = new Vector3d(passengerVel.getX(), 0.0, passengerVel.getZ());
                if (push.lengthSquared() > 0.0001) {
                    var forward = new Vector3d(0, 0, 1).mul(this.basis);

                    double linearPush = forward.dot(push) * 2.0;
                    this.trackVelocity += linearPush;
                    passenger.setVelocity(Vec3d.ZERO);
                }

                var gradeVec = new Vector3d(0, 1, 0).mul(this.basis);
                gradeVec.mul(1, 0, 1);
                int power = Math.max(startE.power(), endE.power());

                this.trackVelocity += gravity;
                this.trackVelocity = startE.nextType().motion.calculate(this.trackVelocity, gradeVec.length(), power);
            }
        } else {
            if (this.hadPassenger) {
                this.destroy();
            }
        }
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        if (this.firstPositionUpdate) {
            this.firstPositionUpdate = false;
            super.updateTrackedPositionAndAngles(x, y, z, yaw, pitch, interpolationSteps);
        }

        this.serverPosition.set(x, y, z);
        this.positionInterpSteps = interpolationSteps + 2;
        this.setAngles(yaw, pitch);
    }

    // This method should be called updateTrackedVelocity, its usage is very similar to the above method
    @Override
    public void setVelocityClient(double x, double y, double z) {
        this.serverVelocity.set(x, y, z);
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        positionUpdater.accept(passenger, this.getX(), this.getY(), this.getZ());
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(ORIENTATION, new Quaternionf().identity());
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);

        if (data.equals(ORIENTATION)) {
            if (this.firstOriUpdate) {
                this.firstOriUpdate = false;
                this.clientOrientation.set(getDataTracker().get(ORIENTATION));
                this.lastClientOrientation.set(this.clientOrientation);
            }
            this.oriInterpSteps = this.getType().getTrackTickInterval() + 2;
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.startTie = SUtil.getBlockPos(nbt, "start");
        this.endTie = SUtil.getBlockPos(nbt, "end");
        this.trackVelocity = nbt.getDouble("track_velocity");
        this.motionScale = nbt.getDouble("motion_scale");
        this.splinePieceProgress = nbt.getDouble("spline_piece_progress");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        SUtil.putBlockPos(nbt, this.startTie, "start");
        SUtil.putBlockPos(nbt, this.endTie, "end");
        nbt.putDouble("track_velocity", this.trackVelocity);
        nbt.putDouble("motion_scale", this.motionScale);
        nbt.putDouble("spline_piece_progress", this.splinePieceProgress);
    }
}
