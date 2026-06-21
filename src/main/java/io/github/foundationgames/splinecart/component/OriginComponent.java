package io.github.foundationgames.splinecart.component;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

import java.util.function.Consumer;

public record OriginComponent(BlockPos pos) implements TooltipProvider {
    public static final Component FIRST_SELECTION = Component.translatable("item.splinecart.track.origin").withStyle(ChatFormatting.YELLOW);
    public static final Component HOW_TO_CLEAR = Component.translatable("item.splinecart.track.clear_hint").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);

    public static final Codec<OriginComponent> CODEC = BlockPos.CODEC.xmap(OriginComponent::new, OriginComponent::pos);

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag type, net.minecraft.core.component.DataComponentGetter getter) {
        tooltip.accept(FIRST_SELECTION);
        tooltip.accept(HOW_TO_CLEAR);
    }
}
