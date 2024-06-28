package io.github.foundationgames.splinecart.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public enum SUtil {;
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
