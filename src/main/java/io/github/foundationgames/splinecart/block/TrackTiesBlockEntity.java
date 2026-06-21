package io.github.foundationgames.splinecart.block;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.TrackType;
import io.github.foundationgames.splinecart.item.TrackItem;
import io.github.foundationgames.splinecart.util.Pose;
import io.github.foundationgames.splinecart.util.SUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

public class TrackTiesBlockEntity extends BlockEntity {
    public float clientTime = 0;
    public final TrackGeometry geometry;

    private TrackType nextType = TrackType.DEFAULT;
    private TrackType prevType = TrackType.DEFAULT;

    private BlockPos next;
    private BlockPos prev;
    private Pose pose;

    private int power = -1;

    public TrackTiesBlockEntity(BlockPos pos, BlockState state) {
        super(Splinecart.TRACK_TIES_BE, pos, state);
        updatePose(pos, state);

        this.geometry = TrackGeometry.CONSTRUCTOR.apply(this);
    }

    public void updatePose(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof TrackTiesBlock ties) {
            this.pose = ties.getPose(state, pos);
        } else {
            this.pose = new Pose(new Vector3d(), new Matrix3d().identity());
        }
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);

        updatePose(this.getBlockPos(), this.getBlockState());
    }

    public static @Nullable TrackTiesBlockEntity of(Level world, @Nullable BlockPos pos) {
        if (pos != null && world.getBlockEntity(pos) instanceof TrackTiesBlockEntity e) {
            return e;
        }

        return null;
    }

    private void dropTrack(TrackType type) {
        var world = getLevel();
        var pos = Vec3.atCenterOf(getBlockPos());
        var item = new ItemEntity(world, pos.x(), pos.y(), pos.z(), new ItemStack(TrackItem.ITEMS_BY_TYPE.get(type)));

        world.addFreshEntity(item);
    }

    public void setNext(@Nullable BlockPos pos, @Nullable TrackType type) {
        if (pos == null) {
            var oldNextE = next();
            this.next = null;
            if (oldNextE != null) {
                oldNextE.prev = null;
                oldNextE.setUpdated();
            }
        } else {
            this.next = pos;
            if (type != null) {
                this.nextType = type;
            }
            var nextE = next();
            if (nextE != null) {
                nextE.prev = getBlockPos();
                if (type != null) {
                    nextE.prevType = type;
                }
                nextE.setUpdated();
            }
        }

        setUpdated();
    }

    public @Nullable TrackTiesBlockEntity next() {
        return of(this.getLevel(), this.next);
    }

    public @Nullable TrackTiesBlockEntity prev() {
        return of(this.getLevel(), this.prev);
    }

    public @Nullable BlockPos nextPos() {
        return next;
    }

    public @Nullable BlockPos prevPos() {
        return prev;
    }

    public TrackType nextType() {
        return this.nextType;
    }

    public TrackType prevType() {
        return this.prevType;
    }

    public Pose pose() {
        return this.pose;
    }

    public void updatePower() {
        int oldPower = this.power;
        this.power = getLevel().getBestNeighborSignal(getBlockPos());

        if (oldPower != this.power) {
            setUpdated();
        }

        var prev = prev();
        if (prev != null) {
            prev.geometry.needsRebuild = true;
        }
    }

    public int power() {
        if (this.power < 0) {
            updatePower();
        }

        return this.power;
    }

    public void onDestroy() {
        if (this.prev != null) {
            this.dropTrack(this.prevType);
        }
        if (this.next != null) {
            this.dropTrack(this.nextType);
        }

        var prevE = prev();
        if (prevE != null) {
            prevE.next = null;
            prevE.setUpdated();
        }
        var nextE = next();
        if (nextE != null) {
            nextE.prev = null;
            nextE.setUpdated();
        }
    }

    public double estimatedTrackLength() {
        var nextE = next();
        if (nextE == null) {
            return 0;
        }

        return Math.sqrt(nextE.getBlockPos().distSqr(this.getBlockPos()));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (!getLevel().isClientSide()) {
            this.onDestroy();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();

        this.geometry.close();
    }

    public void setUpdated() {
        sync();
        setChanged();

        if (getLevel().isClientSide()) {
            this.geometry.needsRebuild = true;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        this.prev = input.read("prev", BlockPos.CODEC).orElse(null);
        this.next = input.read("next", BlockPos.CODEC).orElse(null);

        this.prevType = TrackType.read(input.getIntOr("prev_id", 0));
        this.nextType = TrackType.read(input.getIntOr("next_id", 0));

        this.power = input.getIntOr("power", 0);

        this.geometry.needsRebuild = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        if (this.prev != null) {
            output.store("prev", BlockPos.CODEC, this.prev);
        }
        if (this.next != null) {
            output.store("next", BlockPos.CODEC, this.next);
        }

        output.putInt("prev_id", this.prevType.write());
        output.putInt("next_id", this.nextType.write());

        output.putInt("power", this.power);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    public void sync() {
        getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }
}
