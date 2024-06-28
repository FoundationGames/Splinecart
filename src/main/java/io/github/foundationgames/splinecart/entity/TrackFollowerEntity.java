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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

public class TrackFollowerEntity extends Entity {
    private static final double COMFORTABLE_SPEED = 0.37;
    private static final double MAX_SPEED = 1.28;
    private static final double MAX_ENERGY = 2.45;
    private static final double FRICTION = 0.986;

    private BlockPos startTie;
    private BlockPos endTie;
    private double splinePieceProgress = 0; // t
    private double motionScale; // t-distance per block
    private double trackVelocity;

    private final Vector3d serverPosition = new Vector3d();
    private int positionInterpSteps;
    private int oriInterpSteps;

    private final Vector3d clientVelocity = new Vector3d();

    private static final TrackedData<Quaternionf> ORIENTATION = DataTracker.registerData(TrackFollowerEntity.class, TrackedDataHandlerRegistry.QUATERNIONF);
    private static final TrackedData<Vector3f> VELOCITY = DataTracker.registerData(TrackFollowerEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    private final Matrix3d basis = new Matrix3d().identity();

    private final Quaternionf lastClientOrientation = new Quaternionf();
    private final Quaternionf clientOrientation = new Quaternionf();

    private boolean hadPassenger = false;

    private boolean firstPositionUpdate = true;
    private boolean firstOriUpdate = true;

    public TrackFollowerEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    public TrackFollowerEntity(World world, Vec3d startPos, BlockPos startTie, BlockPos endTie, Vec3d velocity) {
        this(Splinecart.TRACK_FOLLOWER, world);

        setStretch(startTie, endTie);
        this.trackVelocity = velocity.multiply(1, 0, 1).length();

        var startE = TrackTiesBlockEntity.of(this.getWorld(), this.startTie);
        if (startE != null) {
            this.setPosition(startPos);
            this.getDataTracker().set(ORIENTATION, startE.pose().basis().getNormalizedRotation(new Quaternionf()));
        }
    }
    public void setStretch(BlockPos start, BlockPos end) {
        this.startTie = start;
        this.endTie = end;

        var startE = TrackTiesBlockEntity.of(this.getWorld(), this.startTie);
        if (startE != null) {
            this.basis.set(startE.pose().basis());
            var endE = TrackTiesBlockEntity.of(this.getWorld(), this.endTie);
            if (endE != null) {
                // Initial approximation of motion scale; from the next tick onward the derivative of the track spline is used
                this.motionScale = 1 / startE.pose().translation().distance(endE.pose().translation());
            } else {
                this.motionScale = 1;
            }
        }

        if (this.splinePieceProgress < 0) {
            this.splinePieceProgress = 0;
        }
    }

    // For more accurate client side position interpolation, we can conveniently use the
    // same cubic hermite spline formula rather than linear interpolation like vanilla,
    // since we have not only the position but also its derivative (velocity)
    protected void interpPos(int step) {
        double t = 1 / (double)step;

        var clientPos = new Vector3d(this.getX(), this.getY(), this.getZ());
        var clientVel = new Vector3d(this.clientVelocity);

        var svf = getDataTracker().get(VELOCITY);
        var serverVel = new Vector3d(svf.x(), svf.y(), svf.z());

        var newClientPos = new Vector3d();
        Pose.cubicHermiteSpline(t, 1, clientPos, clientVel, this.serverPosition, serverVel,
                newClientPos, this.clientVelocity);

        this.setPosition(newClientPos.x(), newClientPos.y(), newClientPos.z());
    }

    @Override
    public void tick() {
        super.tick();

        var world = this.getWorld();
        if (world.isClient()) {
            if (this.positionInterpSteps > 0) {
                this.interpPos(this.positionInterpSteps);
                this.positionInterpSteps--;
            } else {
                this.refreshPosition();
                this.setRotation(this.getYaw(), this.getPitch());
            }

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

    public Vector3dc getClientVelocity() {
        return this.clientVelocity;
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

                double velocity = Math.min(this.trackVelocity, MAX_SPEED);
                this.splinePieceProgress += velocity * this.motionScale;
                if (this.splinePieceProgress > 1) {
                    this.splinePieceProgress -= 1;

                    var nextE = endE.next();
                    if (nextE == null) {
                        passenger.stopRiding();

                        var newVel = new Vector3d(0, 0, this.trackVelocity).mul(this.basis);
                        passenger.setVelocity(newVel.x(), newVel.y(), newVel.z());
                        this.destroy();
                        return;
                    } else {
                        this.setStretch(this.endTie, nextE.getPos());
                        startE = endE;
                        endE = nextE;
                    }
                }

                var pos = new Vector3d();
                var grad = new Vector3d(); // Change in position per change in spline progress
                startE.pose().interpolate(endE.pose(), this.splinePieceProgress, pos, this.basis, grad);

                var gravity = (getY() - pos.y()) * 0.047;

                this.setPosition(pos.x(), pos.y(), pos.z());
                this.getDataTracker().set(ORIENTATION, this.basis.getNormalizedRotation(new Quaternionf()));
                this.motionScale = 1 / grad.length();

                double dt = this.trackVelocity * this.motionScale; // Change in spline progress per tick
                this.getDataTracker().set(VELOCITY, // Change in position per tick (velocity)
                        new Vector3f((float) grad.x(), (float) grad.y(), (float) grad.z()).mul((float) dt));

                this.trackVelocity = MathHelper.clamp(
                        this.trackVelocity + gravity,
                        Math.min(this.trackVelocity, COMFORTABLE_SPEED),
                        Math.max(this.trackVelocity, MAX_ENERGY));

                if (this.trackVelocity > COMFORTABLE_SPEED) {
                    double diff = this.trackVelocity - COMFORTABLE_SPEED;
                    diff *= FRICTION;
                    this.trackVelocity = COMFORTABLE_SPEED + diff;
                }
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
        this.positionInterpSteps = interpolationSteps + 1;
        this.setAngles(yaw, pitch);
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        positionUpdater.accept(passenger, this.getX(), this.getY(), this.getZ());
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(ORIENTATION, new Quaternionf().identity())
                .add(VELOCITY, new Vector3f());
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
            this.oriInterpSteps = this.getType().getTrackTickInterval() + 1;
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("start")) {
            this.startTie = SUtil.getBlockPos(nbt, "start");
        } else this.startTie = null;
        if (nbt.contains("end")) {
            this.endTie = SUtil.getBlockPos(nbt, "end");
        } else this.endTie = null;
        this.trackVelocity = nbt.getDouble("track_velocity");
        this.motionScale = nbt.getDouble("motion_scale");
        this.splinePieceProgress = nbt.getDouble("spline_piece_progress");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (this.startTie != null) {
            SUtil.putBlockPos(nbt, this.startTie, "start");
        }
        if (this.endTie != null) {
            SUtil.putBlockPos(nbt, this.endTie, "end");
        }
        nbt.putDouble("track_velocity", this.trackVelocity);
        nbt.putDouble("motion_scale", this.motionScale);
        nbt.putDouble("spline_piece_progress", this.splinePieceProgress);
    }
}
