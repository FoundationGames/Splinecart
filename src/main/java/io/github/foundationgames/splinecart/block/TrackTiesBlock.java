package io.github.foundationgames.splinecart.block;

import com.mojang.serialization.MapCodec;
import io.github.foundationgames.splinecart.item.TrackItem;
import io.github.foundationgames.splinecart.util.Pose;
import io.github.foundationgames.splinecart.util.SplineSegment;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;

public class TrackTiesBlock extends DirectionalBlock implements EntityBlock {
    public static final MapCodec<TrackTiesBlock> CODEC = simpleCodec(TrackTiesBlock::new);
    public static final IntegerProperty POINTING = IntegerProperty.create("pointing", 0, 3);

    public static final VoxelShape[] SHAPES = new VoxelShape[Direction.values().length];

    public TrackTiesBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP).setValue(POINTING, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, POINTING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var side = ctx.getClickedFace();
        var hdir = ctx.getHorizontalDirection();
        int rot;

        switch (side) {
            case DOWN -> rot = Math.floorMod(2 + hdir.get2DDataValue(), 4);
            case UP -> rot = Math.floorMod(2 - hdir.get2DDataValue(), 4);
            default -> {
                int hos = Math.floorMod(2 + hdir.getOpposite().get2DDataValue() - side.get2DDataValue(), 4) - 2;
                if (hos == 0) {
                    var player = ctx.getPlayer();
                    float pitch = 0;
                    if (player != null) {
                        pitch = player.getXRot();
                    }
                    rot = pitch <= 0 ? 2 : 0;
                } else {
                    rot = hos > 0 ? 1 : 3;
                }
            }
        }

        return defaultBlockState().setValue(FACING, ctx.getClickedFace()).setValue(POINTING, rot);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).ordinal()];
    }



    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, @Nullable Orientation neighborPos, boolean moved) {
        super.neighborChanged(state, world, pos, sourceBlock, neighborPos, moved);

        if (world.getBlockEntity(pos) instanceof TrackTiesBlockEntity tie) {
            tie.updatePower();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.mayBuild() &&
                !(player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof TrackItem) &&
                world.getBlockEntity(pos) instanceof TrackTiesBlockEntity tie) {
            if (tie.prev() == null && tie.next() == null) {
                if (world.isClientSide()) {
                    return InteractionResult.SUCCESS;
                } else {
                    var newState = state.setValue(POINTING, (state.getValue(POINTING) + 1) % 4);
                    world.setBlock(pos, newState, 3);
                    tie.updatePose(pos, newState);
                    tie.setChanged();
                    tie.sync();

                    return InteractionResult.CONSUME;
                }
            }
        }

        return super.useWithoutItem(state, world, pos, player, hit);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof MinecartItem) ||
                !(world.getBlockEntity(pos) instanceof TrackTiesBlockEntity tie)) {
            return super.useItemOn(stack, state, world, pos, player, hand, hit);
        }

        var segment = nearestStraightSegment(tie, hit);
        var cartType = minecartType(stack.getItem());
        if (segment == null || cartType == null) {
            return super.useItemOn(stack, state, world, pos, player, hand, hit);
        }

        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        var hitPoint = new Vector3d(hit.getLocation().x(), hit.getLocation().y(), hit.getLocation().z());
        double progress = segment.nearestStraightProgress(hitPoint);
        var spawn = new Vector3d();
        var basis = new Matrix3d();
        var tangent = new Vector3d();
        segment.interpolate(progress, spawn, basis, tangent);
        tangent.normalize();

        var cart = AbstractMinecart.createMinecart(world, spawn.x(), spawn.y(), spawn.z(), cartType,
                EntitySpawnReason.SPAWN_ITEM_USE, stack, player);
        if (cart == null) {
            return InteractionResult.FAIL;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-tangent.x(), tangent.z()));
        cart.absSnapRotationTo(yaw, 0.0f);

        // Ignore the thin clicked rail base, but reject blocks occupying the cart body.
        AABB body = cart.getBoundingBox();
        body = new AABB(body.minX, spawn.y() + 0.125, body.minZ, body.maxX, body.maxY, body.maxZ);
        if (!world.noCollision(cart, body) || !world.getEntities(cart, cart.getBoundingBox(), Entity::isPickable).isEmpty()) {
            return InteractionResult.FAIL;
        }

        var follower = TrackFollowerEntity.createPlaced(world, segment.start().getBlockPos(), progress);
        if (follower == null || !(world instanceof ServerLevel serverLevel)) {
            return InteractionResult.FAIL;
        }

        serverLevel.addFreshEntity(follower);
        serverLevel.addFreshEntity(cart);
        cart.startRiding(follower, true, false);
        stack.consume(1, player);
        serverLevel.playSound(null, BlockPos.containing(spawn.x(), spawn.y(), spawn.z()),
                SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
        serverLevel.gameEvent(GameEvent.ENTITY_PLACE, BlockPos.containing(spawn.x(), spawn.y(), spawn.z()),
                GameEvent.Context.of(player, state));
        return InteractionResult.SUCCESS;
    }

    private static @Nullable SplineSegment nearestStraightSegment(TrackTiesBlockEntity tie, BlockHitResult hit) {
        var candidates = new ArrayList<SplineSegment>(2);
        var next = tie.next();
        if (next != null) candidates.add(new SplineSegment(tie, next));
        var prev = tie.prev();
        if (prev != null) candidates.add(new SplineSegment(prev, tie));

        var point = new Vector3d(hit.getLocation().x(), hit.getLocation().y(), hit.getLocation().z());
        SplineSegment nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (var candidate : candidates) {
            if (!candidate.isStraightHorizontal()) continue;
            double t = candidate.nearestStraightProgress(point);
            var position = new Vector3d();
            candidate.interpolate(t, position, new Matrix3d(), new Vector3d());
            double distance = position.distanceSquared(point);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static @Nullable EntityType<? extends AbstractMinecart> minecartType(Item item) {
        if (item == Items.MINECART) return EntityType.MINECART;
        if (item == Items.CHEST_MINECART) return EntityType.CHEST_MINECART;
        if (item == Items.FURNACE_MINECART) return EntityType.FURNACE_MINECART;
        if (item == Items.TNT_MINECART) return EntityType.TNT_MINECART;
        if (item == Items.HOPPER_MINECART) return EntityType.HOPPER_MINECART;
        if (item == Items.COMMAND_BLOCK_MINECART) return EntityType.COMMAND_BLOCK_MINECART;
        return null;
    }

    public Pose getPose(BlockState state, BlockPos pos) {
        if (state.hasProperty(FACING) && state.hasProperty(POINTING)) {
            var face = state.getValue(FACING);
            int point = state.getValue(POINTING);

            return getPose(pos, face, point);
        }

        return null;
    }

    public static Pose getPose(BlockPos block, Direction normal, int point) {
        var pos = new Vector3d();
        var basis = new Matrix3d().identity();

        pos.set(normal.getStepX(), normal.getStepY(), normal.getStepZ()).mul(-0.4375).add(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);

        if (normal == Direction.UP || normal == Direction.DOWN) {
            point += 2;
        }

        var axisAngle = new AxisAngle4d(point * Mth.PI * 0.5, normal.getStepX(), normal.getStepY(), normal.getStepZ());
        basis.rotate(axisAngle);
        basis.rotate(normal.getRotation());

        return new Pose(pos, basis);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrackTiesBlockEntity(pos, state);
    }

    static {
        for (var dir : Direction.values()) {
            int idx = dir.ordinal();
            var min = new Vector3d(-8, -8, -8);
            var max = new Vector3d(8, -6, 8);

            var rot = dir.getRotation();
            rot.transform(min);
            rot.transform(max);

            min.add(8, 8, 8);
            max.add(8, 8, 8);

            SHAPES[idx] = Block.box(
                    Math.min(min.x(), max.x()),
                    Math.min(min.y(), max.y()),
                    Math.min(min.z(), max.z()),
                    Math.max(min.x(), max.x()),
                    Math.max(min.y(), max.y()),
                    Math.max(min.z(), max.z()));
        }
    }
}
