package io.github.foundationgames.splinecart.entity;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.util.SUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix3d;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public class TrackFollowerEntity extends Entity {
    private BlockPos startTie;
    private BlockPos endTie;
    private double splinePieceProgress = 0; // t
    private double motionScale; // t-distance per block
    private double trackVelocity;

    private double interpX, interpY, interpZ;
    private int positionInterpSteps;
    private int oriInterpSteps;

    private static final TrackedData<Quaternionf> ORIENTATION = DataTracker.registerData(TrackFollowerEntity.class, TrackedDataHandlerRegistry.QUATERNIONF);
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
                // Someday: curve length calculation
                this.motionScale = 1 / startE.pose().translation().distance(endE.pose().translation());
            } else {
                this.motionScale = 1;
            }
        }
        this.splinePieceProgress = 0;
    }

    @Override
    public void tick() {
        super.tick();

        var world = this.getWorld();
        if (world.isClient()) {
            this.clientMotion = this.getPos().negate();
            if (this.positionInterpSteps > 0) {
                this.lerpPosAndRotation(this.positionInterpSteps, this.interpX, this.interpY, this.interpZ, 0, 0);
                this.positionInterpSteps--;
            } else {
                this.refreshPosition();
                this.setRotation(this.getYaw(), this.getPitch());
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

    protected void updateServer() {
        var passenger = this.getFirstPassenger();
        if (passenger != null) {
            if (!hadPassenger) {
                hadPassenger = true;
            } else {
                var world = this.getWorld();
                var startE = TrackTiesBlockEntity.of(world, this.startTie);
                var endE = TrackTiesBlockEntity.of(world, this.endTie);
                if (startE == null || endE == null) {
                    this.remove(RemovalReason.KILLED);
                    return;
                }

                this.splinePieceProgress += this.trackVelocity * this.motionScale;
                if (this.splinePieceProgress > 1) {
                    this.splinePieceProgress -= 1;

                    var nextE = startE.next();
                    if (nextE == null) {
                        passenger.stopRiding();

                        var newVel = new Vector3d(0, 0, this.trackVelocity).mul(this.basis);
                        passenger.setVelocity(newVel.x(), newVel.y(), newVel.z());
                        this.remove(RemovalReason.KILLED);
                    } else {
                        this.setStretch(this.endTie, nextE.getPos());
                    }
                    return;
                }

                var pos = new Vector3d();
                var grad = new Vector3d();
                startE.pose().interpolate(endE.pose(), this.splinePieceProgress, pos, this.basis, grad);

                this.setPosition(pos.x(), pos.y(), pos.z());
                this.getDataTracker().set(ORIENTATION, this.basis.getNormalizedRotation(new Quaternionf()));
            }
        } else {
            if (this.hadPassenger) {
                this.remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        if (this.firstPositionUpdate) {
            this.firstPositionUpdate = false;
            super.updateTrackedPositionAndAngles(x, y, z, yaw, pitch, interpolationSteps);
        }

        this.interpX = x;
        this.interpY = y;
        this.interpZ = z;
        this.positionInterpSteps = interpolationSteps + 1;
        this.setAngles(yaw, pitch);
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
