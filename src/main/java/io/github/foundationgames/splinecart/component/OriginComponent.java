package io.github.foundationgames.splinecart.component;

import com.mojang.serialization.Codec;
import net.minecraft.util.math.BlockPos;

public record OriginComponent(BlockPos pos) {
    public static final Codec<OriginComponent> CODEC = BlockPos.CODEC.xmap(OriginComponent::new, OriginComponent::pos);
}
