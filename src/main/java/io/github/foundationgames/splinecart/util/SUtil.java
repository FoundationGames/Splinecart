package io.github.foundationgames.splinecart.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

public enum SUtil {;
    public static void putBlockPos(NbtCompound nbt, BlockPos pos, String key) {
        nbt.putIntArray(key, new int[] {pos.getX(), pos.getY(), pos.getZ()});
    }

    public static BlockPos getBlockPos(NbtCompound nbt, String key) {
        var arr = nbt.getIntArray(key);
        if (arr.length < 3) return null;

        return new BlockPos(arr[0], arr[1], arr[2]);
    }
}
