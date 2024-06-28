package io.github.foundationgames.splinecart.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @ModifyExpressionValue(method = "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V",
            at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;method_52836()Z"))
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
}
