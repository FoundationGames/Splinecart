package io.github.foundationgames.splinecart;

import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;

public class SplinecartHud implements HudRenderCallback {
    public static final Text CANCEL = Text.translatable("hud.splinecart.cancel").formatted(Formatting.RED);
    public static final Text CREATE = Text.translatable("hud.splinecart.create_track").formatted(Formatting.GREEN);
    public static final String RIGHT_CLICK_HINT = "hud.splinecart.right_click";

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        var client = MinecraftClient.getInstance();
        var world = client.world;

        if (world != null && client.player != null) {
            var origin = client.player.getMainHandStack().get(Splinecart.ORIGIN_POS);

            if (origin == null) {
                origin = client.player.getOffHandStack().get(Splinecart.ORIGIN_POS);
            }

            if (origin != null && client.crosshairTarget instanceof BlockHitResult hit) {
                var pos = hit.getBlockPos();
                if (world.getBlockState(pos).isAir()) {
                    return;
                }

                var hint = CANCEL;

                if (!pos.equals(origin.pos()) && world.getBlockEntity(pos) instanceof TrackTiesBlockEntity ties && ties.prev() == null) {
                    hint = CREATE;
                }

                int w = drawContext.getScaledWindowWidth();
                int h = drawContext.getScaledWindowHeight();

                var text = Text.translatable(RIGHT_CLICK_HINT, client.options.useKey.getBoundKeyLocalizedText(), hint);
                drawContext.drawCenteredTextWithShadow(client.textRenderer, text, w / 2, (h / 2) + 20, 0xFFFFFFFF);
            }
        }
    }
}
