package io.github.foundationgames.splinecart;

import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.BlockHitResult;

public class SplinecartHud implements HudElement {
    public static final Component CANCEL = Component.translatable("hud.splinecart.cancel").withStyle(ChatFormatting.RED);
    public static final Component CREATE = Component.translatable("hud.splinecart.create_track").withStyle(ChatFormatting.GREEN);
    public static final String RIGHT_CLICK_HINT = "hud.splinecart.right_click";

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, DeltaTracker tickCounter) {
        var client = Minecraft.getInstance();
        var world = client.level;

        if (world != null && client.player != null) {
            var origin = client.player.getMainHandItem().get(Splinecart.ORIGIN_POS);

            if (origin == null) {
                origin = client.player.getOffhandItem().get(Splinecart.ORIGIN_POS);
            }

            if (origin != null && client.hitResult instanceof BlockHitResult hit) {
                var pos = hit.getBlockPos();
                if (world.getBlockState(pos).isAir()) {
                    return;
                }

                var hint = CANCEL;

                if (!pos.equals(origin.pos()) && world.getBlockEntity(pos) instanceof TrackTiesBlockEntity ties && ties.prev() == null) {
                    hint = CREATE;
                }

                int w = guiGraphics.guiWidth();
                int h = guiGraphics.guiHeight();

                var text = Component.translatable(RIGHT_CLICK_HINT, client.options.keyUse.getTranslatedKeyMessage(), hint);
                guiGraphics.centeredText(client.font, text, w / 2, (h / 2) + 20, 0xFFFFFFFF);
            }
        }
    }
}
