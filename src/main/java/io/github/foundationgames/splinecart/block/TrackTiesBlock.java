package io.github.foundationgames.splinecart.block;

import com.mojang.serialization.MapCodec;
import io.github.foundationgames.splinecart.util.Pose;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.FacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Matrix3d;
import org.joml.Vector3d;

public class TrackTiesBlock extends FacingBlock implements BlockEntityProvider {
    public static final MapCodec<TrackTiesBlock> CODEC = createCodec(TrackTiesBlock::new);
    public static final IntProperty POINTING = IntProperty.of("pointing", 0, 3);

    public TrackTiesBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.UP).with(POINTING, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(FACING, POINTING);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getSide());
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (world.getBlockEntity(pos) instanceof TrackTiesBlockEntity tie) {
            if (!newState.isOf(state.getBlock())) {
                if (!world.isClient()) tie.onDestroy();
            } else {
                tie.updatePose(pos, newState);
            }
        }

        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player.getStackInHand(Hand.MAIN_HAND).isEmpty() && world.getBlockEntity(pos) instanceof TrackTiesBlockEntity tie) {
            if (tie.prev() == null && tie.next() == null) {
                if (world.isClient()) {
                    return ActionResult.SUCCESS;
                } else {
                    var newState = state.with(POINTING, (state.get(POINTING) + 1) % 4);
                    world.setBlockState(pos, newState);
                    tie.updatePose(pos, newState);
                    tie.markDirty();
                    tie.sync();
                }
            }
        }

        return super.onUse(state, world, pos, player, hit);
    }

    public Pose getPose(BlockState state, BlockPos pos) {
        if (state.contains(FACING) && state.contains(POINTING)) {
            var face = state.get(FACING);
            int point = state.get(POINTING);

            return getPose(pos, face, point);
        }

        return null;
    }

    public static Pose getPose(BlockPos block, Direction normal, int point) {
        var pos = new Vector3d();
        var basis = new Matrix3d().identity();

        var normVec = normal.getVector();
        pos.set(normVec.getX(), normVec.getY(), normVec.getZ()).mul(-0.4375).add(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);

        if (normal == Direction.UP || normal == Direction.DOWN) {
            point += 2;
        }

        var axisAngle = new AxisAngle4d(point * MathHelper.PI * 0.5, normVec.getX(), normVec.getY(), normVec.getZ());
        basis.rotate(axisAngle);
        basis.rotate(normal.getRotationQuaternion());

        return new Pose(pos, basis);
    }

    @Override
    protected MapCodec<? extends FacingBlock> getCodec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TrackTiesBlockEntity(pos, state);
    }
}
