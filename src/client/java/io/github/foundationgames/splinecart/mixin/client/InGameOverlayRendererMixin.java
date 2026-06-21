package io.github.foundationgames.splinecart.mixin.client;

import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenEffectRenderer.class)
public class InGameOverlayRendererMixin {
    @Inject(method = "getViewBlockingState",
            at = @At("HEAD"), cancellable = true)
    private static void splinecart$modifySuffocatingBlock(Player player, CallbackInfoReturnable<BlockState> info) {
        var vehicle = player.getVehicle();
        while (vehicle != null) {
            if (vehicle instanceof TrackFollowerEntity) {
                var world = player.level();
                var pos = BlockPos.containing(player.getEyePosition());
                var state = world.getBlockState(pos);

                if (state.getRenderShape() != RenderShape.INVISIBLE && state.isViewBlocking(world, pos)) {
                    info.setReturnValue(state);
                } else {
                    info.setReturnValue(null);
                }
                return;
            }

            vehicle = vehicle.getVehicle();
        }
    }
}
