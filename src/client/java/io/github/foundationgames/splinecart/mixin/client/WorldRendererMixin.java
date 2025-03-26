package io.github.foundationgames.splinecart.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.block.entity.TrackTiesBlockEntityRenderer;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ConcurrentModificationException;
import java.util.Set;

@Mixin(value = {WorldRenderer.class}, priority = 1500)
public class WorldRendererMixin {
    @Shadow @Final private Set<BlockEntity> noCullingBlockEntities;
    @Shadow @Final private MinecraftClient client;

    @ModifyExpressionValue(method = "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V",
            require = 0, at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;method_52836()Z"))
    private boolean splinecart$updateChunkOcclusionCullingWhileOnTrack(boolean old) {
        if (SplinecartClient.CFG_ROTATE_CAMERA.get()) {
            var entity = MinecraftClient.getInstance().cameraEntity;
            while (entity != null) {
                entity = entity.getVehicle();

                if (entity instanceof TrackFollowerEntity) {
                    return true;
                }
            }
        }

        return old;
    }

    @Inject(method = "updateBlock(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;I)V", at = @At("TAIL"))
    private void splinecart$updateBlockEntityVbos(BlockView world, BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (SplinecartClient.CFG_VBOS.get()) {
            var chunkPos = ChunkSectionPos.from(pos);
            try {
                TrackTiesBlockEntityRenderer.queueVboRebuildsForChunkUpdate(chunkPos.getX(), chunkPos.getY(), chunkPos.getZ(), noCullingBlockEntities);
            } catch (ConcurrentModificationException ignored) {}
        }
    }
}
