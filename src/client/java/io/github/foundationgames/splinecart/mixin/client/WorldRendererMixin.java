package io.github.foundationgames.splinecart.mixin.client;

import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin {
    @ModifyArg(method = "cullTerrain",
               at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;)V"),
               index = 0,
               require = 0)
    private boolean splinecart$disableOcclusionCullingWhileOnTrack(boolean originalSmartCull) {
        if (SplinecartClient.CFG_ROTATE_CAMERA.get()) {
            var entity = Minecraft.getInstance().getCameraEntity();
            while (entity != null) {
                entity = entity.getVehicle();
                if (entity instanceof TrackFollowerEntity) {
                    return false;
                }
            }
        }
        return originalSmartCull;
    }
}
