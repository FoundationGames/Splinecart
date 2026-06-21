package io.github.foundationgames.splinecart.util;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.util.function.BiFunction;
import java.util.function.DoubleSupplier;

public enum SUtil {;
    public static final Vector3f[] REDSTONE_COLOR_LUT;

    static {
        REDSTONE_COLOR_LUT = new Vector3f[16];
        for (int i = 0; i <= 15; i++) {
            float strength = (float)i / 15.0F;
            REDSTONE_COLOR_LUT[i] = new Vector3f(
                    strength * 0.6f + (strength > 0.0f ? 0.4f : 0.3f),
                    Mth.clamp((strength * strength * 0.7f) - 0.5f, 0, 1),
                    Mth.clamp((strength * strength * 0.6f) - 0.7f, 0, 1)
            );
        }
    }

    public static final Quaternionf BACKWARDS = Axis.YP.rotation(Mth.PI);
    public static DoubleSupplier TICK_DELTA = () -> 0;

    public static <V, T extends V> T register(Registry<V> registry, Identifier id, BiFunction<Identifier, ResourceKey<V>, T> obj) {
        ResourceKey<V> key = ResourceKey.create(registry.key(), id);
        return Registry.register(registry, id, obj.apply(id, key));
    }

    public static boolean failsSanityCheck(Vector3dc vec) {
        return Double.isNaN(vec.x()) || Double.isNaN(vec.y()) || Double.isNaN(vec.z());
    }

    public static boolean failsSanityCheck(Quaternionf rot) {
        return Double.isNaN(rot.x()) || Double.isNaN(rot.y()) || Double.isNaN(rot.z()) || Double.isNaN(rot.w());
    }
}
