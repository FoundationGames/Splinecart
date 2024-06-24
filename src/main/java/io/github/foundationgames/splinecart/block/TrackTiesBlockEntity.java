package io.github.foundationgames.splinecart.block;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.util.Pose;
import io.github.foundationgames.splinecart.util.SUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

public class TrackTiesBlockEntity extends BlockEntity {
    private BlockPos next;
    private BlockPos prev;
    private Pose pose;

    public TrackTiesBlockEntity(BlockPos pos, BlockState state) {
        super(Splinecart.TRACK_TIES_BE, pos, state);
        updatePose(pos, state);
    }

    public void updatePose(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof TrackTiesBlock ties) {
            this.pose = ties.getPose(state, pos);
        } else {
            this.pose = new Pose(new Vector3d(), new Matrix3d().identity());
        }
    }

    @Override
    public void setCachedState(BlockState state) {
        super.setCachedState(state);

        updatePose(this.getPos(), this.getCachedState());
    }

    public static @Nullable TrackTiesBlockEntity of(World world, @Nullable BlockPos pos) {
        if (pos != null && world.getBlockEntity(pos) instanceof TrackTiesBlockEntity e) {
            return e;
        }

        return null;
    }

    private void dropTrack() {
        var world = getWorld();
        var pos = Vec3d.ofCenter(getPos());
        var item = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Splinecart.TRACK));

        world.spawnEntity(item);
    }

    public void setNext(@Nullable BlockPos pos) {
        if (pos == null) {
            var oldNextE = next();
            this.next = null;
            if (oldNextE != null) {
                this.dropTrack();

                oldNextE.prev = null;
                oldNextE.markDirty();
                oldNextE.sync();
            }
        } else {
            this.next = pos;
            var nextE = next();
            if (nextE != null) {
                nextE.prev = getPos();
                nextE.markDirty();
                nextE.sync();
            }
        }

        markDirty();
        sync();
    }

    public @Nullable TrackTiesBlockEntity next() {
        return of(this.getWorld(), this.next);
    }

    public @Nullable TrackTiesBlockEntity prev() {
        return of(this.getWorld(), this.prev);
    }

    public Pose pose() {
        return this.pose;
    }

    public void onDestroy() {
        var prevE = prev();
        if (prevE != null) {
            prevE.next = null;
            prevE.markDirty();
            prevE.sync();
        }

        this.setNext(null);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);

        if (nbt.contains("prev")) {
            this.prev = SUtil.getBlockPos(nbt, "prev");
        } else this.prev = null;
        if (nbt.contains("next")) {
            this.next = SUtil.getBlockPos(nbt, "next");
        } else this.next = null;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);

        if (this.prev != null) {
            SUtil.putBlockPos(nbt, this.prev, "prev");
        }
        if (this.next != null) {
            SUtil.putBlockPos(nbt, this.next, "next");
        }
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        var nbt = super.toInitialChunkDataNbt(registryLookup);
        writeNbt(nbt, registryLookup);
        return nbt;
    }

    public void sync() {
        getWorld().updateListeners(getPos(), getCachedState(), getCachedState(), 3);
    }
}
