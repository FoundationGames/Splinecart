package io.github.foundationgames.splinecart.component;

import com.mojang.serialization.Codec;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipAppender;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.function.Consumer;

public record OriginComponent(BlockPos pos) implements TooltipAppender {
    public static final Text FIRST_SELECTION = Text.translatable("item.splinecart.track.origin").formatted(Formatting.YELLOW);
    public static final Text HOW_TO_CLEAR = Text.translatable("item.splinecart.track.clear_hint").formatted(Formatting.GOLD, Formatting.ITALIC);

    public static final Codec<OriginComponent> CODEC = BlockPos.CODEC.xmap(OriginComponent::new, OriginComponent::pos);

    @Override
    public void appendTooltip(Item.TooltipContext context, Consumer<Text> tooltip, TooltipType type) {
        tooltip.accept(FIRST_SELECTION);
        tooltip.accept(HOW_TO_CLEAR);
    }
}
