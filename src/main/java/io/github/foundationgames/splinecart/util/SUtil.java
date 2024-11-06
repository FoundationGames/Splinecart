package io.github.foundationgames.splinecart.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public enum SUtil {;
    public static final Vector3f[] REDSTONE_COLOR_LUT = Util.make(new Vector3f[16], colors -> {
        for (int i = 0; i <= 15; i++) {
            float strength = (float)i / 15.0F;
            colors[i] = new Vector3f(
                    strength * 0.6f + (strength > 0.0f ? 0.4f : 0.3f),
                    MathHelper.clamp((strength * strength * 0.7f) - 0.5f, 0, 1),
                    MathHelper.clamp((strength * strength * 0.6f) - 0.7f, 0, 1)
            );
        }
    });

    public static void putBlockPos(NbtCompound nbt, @Nullable BlockPos pos, String key) {
        if (pos == null) {
            nbt.putIntArray(key, new int[0]);
        } else nbt.putIntArray(key, new int[] {pos.getX(), pos.getY(), pos.getZ()});
    }

    public static BlockPos getBlockPos(NbtCompound nbt, String key) {
        var arr = nbt.getIntArray(key);
        if (arr.length < 3) return null;

        return new BlockPos(arr[0], arr[1], arr[2]);
    }
}
