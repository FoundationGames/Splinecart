package io.github.foundationgames.splinecart.entity;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.TrackType;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.util.SUtil;
import io.github.foundationgames.splinecart.util.TrackProgress;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

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

    private int progInterpSteps;
    private final InterpolationHandler interpolation = new InterpolationHandler(this);

    private static final EntityDataAccessor<TrackProgress> TRACK_PROGRESS = SynchedEntityData.defineId(TrackFollowerEntity.class, TrackProgress.DATA_HANDLER);
    public static final EntityDataAccessor<Vector3fc> TRACK_MOTION = SynchedEntityData.defineId(TrackFollowerEntity.class, EntityDataSerializers.VECTOR3);
    private final Matrix3d basis = new Matrix3d().identity();

    private final Quaternionf lastClientOrientation = new Quaternionf();
    private final Quaternionf clientOrientation = new Quaternionf();
    private final Quaternionf targetClientOrientation = new Quaternionf();

    private boolean hadPassenger = false;

    private boolean firstProgUpdate = true;

    public TrackFollowerEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public TrackFollowerEntity(Level world) {
        this(Splinecart.TRACK_FOLLOWER, world);
    }

    public static @Nullable TrackFollowerEntity create(Level world, Vec3 startPos, BlockPos tie, Vec3 velocity) {
        var tieE = TrackTiesBlockEntity.of(world, tie);
        double trackVelocity, progress;
        BlockPos start, end;
        if (tieE != null) {
            var tieDir = new Vector3d(0, 0, 1).mul(tieE.pose().basis()).normalize();
            var velDir = new Vector3d(velocity.x, velocity.y, velocity.z).normalize();

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
            follower.setPos(startPos.x, startPos.y, startPos.z);
            follower.getEntityData().set(TRACK_PROGRESS, TrackProgress.of(startE, progress));

            return follower;
        }

        return null;
    }

    public static @Nullable TrackFollowerEntity createPlaced(Level world, BlockPos startTie, double progress) {
        var startE = TrackTiesBlockEntity.of(world, startTie);
        var endE = startE != null ? startE.next() : null;
        if (startE == null || endE == null) {
            return null;
        }

        var follower = new TrackFollowerEntity(world);
        follower.trackVelocity = 0.0;
        follower.splinePieceProgress = Mth.clamp(progress, 0.0, 1.0);
        follower.setStretch(startTie, endE.getBlockPos());

        var position = new Vector3d();
        var derivative = new Vector3d();
        startE.pose().interpolate(endE.pose(), follower.splinePieceProgress, position, follower.basis, derivative);
        follower.motionScale = derivative.lengthSquared() > 1.0e-8 ? 1.0 / derivative.length() : 0.0;
        follower.setPos(position.x(), position.y(), position.z());
        follower.getEntityData().set(TRACK_PROGRESS, TrackProgress.of(startE, follower.splinePieceProgress));
        return follower;
    }

    public void setStretch(@Nullable BlockPos start, @Nullable BlockPos end) {
        this.startTie = start;
        this.endTie = end;
    }

    @Override
    public void tick() {
        super.tick();

        var world = this.level();
        if (world.isClientSide()) {
            var passenger = this.getFirstPassenger();
            if (passenger != null) {
                passenger.setYRot(90);
                passenger.setYHeadRot(90);
            }

            this.interpolation.interpolate();
            this.lastClientOrientation.set(this.clientOrientation);

            if (this.progInterpSteps > 0) {
                float delta = 1 / (float) this.progInterpSteps;
                this.clientOrientation.slerp(this.targetClientOrientation, delta);
                this.progInterpSteps--;
            } else {
                this.clientOrientation.set(this.targetClientOrientation);
            }
        } else {
            this.updateServer();
        }
    }

    public void getClientOrientation(Quaternionf q, float tickDelta) {
        this.lastClientOrientation.slerp(this.clientOrientation, tickDelta, q);
    }

    public Vector3fc getClientMotion() {
        return this.entityData.get(TRACK_MOTION);
    }

    public Matrix3dc getServerBasis() {
        return this.basis;
    }

    public void destroy() {
        this.remove(RemovalReason.KILLED);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        return false;
    }

    private void flyOffTrack(Entity firstPassenger) {
        firstPassenger.stopRiding();

        var newVel = new Vector3d(0, 0, this.trackVelocity).mul(this.basis);
        firstPassenger.setDeltaMovement(new Vec3(newVel.x(), newVel.y(), newVel.z()));
        this.destroy();
    }

    protected void updateServer() {
        for (var passenger : this.getPassengers()) {
            passenger.fallDistance = 0;
        }

        var passenger = this.getFirstPassenger();
        if (passenger != null) {
            passenger.setYRot(90);
            passenger.setYHeadRot(90);

            if (!hadPassenger) {
                hadPassenger = true;
            } else {
                var world = this.level();
                var startE = TrackTiesBlockEntity.of(world, this.startTie);
                var endE = TrackTiesBlockEntity.of(world, this.endTie);
                if (startE == null || endE == null) {
                    this.destroy();
                    return;
                }

                var motion = new Vector3f((float) -getX(), (float) -getY(), (float) -getZ());

                this.splinePieceProgress += this.trackVelocity * this.motionScale;
                if (this.splinePieceProgress > 1) {
                    this.splinePieceProgress -= 1;

                    var nextE = endE.next();
                    if (nextE == null) {
                        this.flyOffTrack(passenger);
                        return;
                    } else {
                        this.setStretch(this.endTie, nextE.getBlockPos());
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
                        this.setStretch(prevE.getBlockPos(), this.startTie);
                        endE = startE;
                        startE = prevE;
                    }
                }

                var pos = new Vector3d();
                var deriv = new Vector3d(); // Change in position per change in spline progress
                startE.pose().interpolate(endE.pose(), this.splinePieceProgress, pos, this.basis, deriv);

                this.setPos(pos.x(), pos.y(), pos.z());
                this.getEntityData().set(TRACK_PROGRESS, TrackProgress.of(startE, this.splinePieceProgress));

                double derivScale = deriv.length();
                if (derivScale >= 0.0000001) {
                    this.motionScale = 1 / derivScale;
                }

                var heading = new Vector3d(deriv).normalize();
                var gravity = -heading.y() * GRAVITY;

                double dt = this.trackVelocity * this.motionScale; // Change in spline progress per tick
                deriv.mul(dt); // Change in position per tick (velocity)
                this.setDeltaMovement(new Vec3(deriv.x(), deriv.y(), deriv.z()));

                motion.add((float) getX(), (float) getY(), (float) getZ());
                this.entityData.set(TRACK_MOTION, motion);

                var passengerVel = passenger.getDeltaMovement();
                var push = new Vector3d(passengerVel.x, 0.0, passengerVel.z);
                if (push.lengthSquared() > 0.0001) {
                    var forward = new Vector3d(0, 0, 1).mul(this.basis);

                    double linearPush = forward.dot(push) * 2.0;
                    this.trackVelocity += linearPush;
                    passenger.setDeltaMovement(Vec3.ZERO);
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

    public Vec3 getLerpedPosition(float partialTicks) {
        double x = this.xo + (this.getX() - this.xo) * partialTicks;
        double y = this.yo + (this.getY() - this.yo) * partialTicks;
        double z = this.zo + (this.getZ() - this.zo) * partialTicks;
        return new Vec3(x, y, z);
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        moveFunction.accept(passenger, this.getX(), this.getY(), this.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TRACK_PROGRESS, TrackProgress.empty(position()));
        builder.define(TRACK_MOTION, new Vector3f());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);

        if (data.equals(TRACK_PROGRESS)) {
            var progress = getEntityData().get(TRACK_PROGRESS);
            if (progress.orientationOnly()) {
                return;
            }

            var targetBasis = new Matrix3d();
            progress.startPose().interpolate(
                    progress.endPose(), progress.t(), new Vector3d(), targetBasis, new Vector3d());
            targetBasis.getNormalizedRotation(this.targetClientOrientation);

            if (this.firstProgUpdate) {
                this.firstProgUpdate = false;
                this.clientOrientation.set(this.targetClientOrientation);
                this.lastClientOrientation.set(this.clientOrientation);
            }

            this.progInterpSteps = this.getType().updateInterval() + 2;
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.startTie = input.read("start", BlockPos.CODEC).orElse(null);
        this.endTie = input.read("end", BlockPos.CODEC).orElse(null);
        this.trackVelocity = input.getDoubleOr("track_velocity", 0.0);
        this.motionScale = input.getDoubleOr("motion_scale", 0.0);
        this.splinePieceProgress = input.getDoubleOr("spline_piece_progress", 0.0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (this.startTie != null) {
            output.store("start", BlockPos.CODEC, this.startTie);
        }
        if (this.endTie != null) {
            output.store("end", BlockPos.CODEC, this.endTie);
        }
        output.putDouble("track_velocity", this.trackVelocity);
        output.putDouble("motion_scale", this.motionScale);
        output.putDouble("spline_piece_progress", this.splinePieceProgress);
    }
}
