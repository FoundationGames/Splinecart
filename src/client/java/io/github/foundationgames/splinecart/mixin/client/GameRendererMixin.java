package io.github.foundationgames.splinecart.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import io.github.foundationgames.splinecart.SplinecartClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.ResourceFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "loadPrograms",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 0, shift = At.Shift.AFTER))
    private void splinecart$registerRenderLayer(ResourceFactory factory, CallbackInfo ci,
                                                @Local(ordinal = 1) List<Pair<ShaderProgram, Consumer<ShaderProgram>>> programs) throws IOException {
        programs.add(Pair.of(
                new ShaderProgram(factory,
                        "splinecart_rendertype_entity_cutout_no_cull_uv_transform",
                        VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
                ),
                program -> SplinecartClient.entityCutoutNoCullUvTransformProgram = program
        ));
    }
}
