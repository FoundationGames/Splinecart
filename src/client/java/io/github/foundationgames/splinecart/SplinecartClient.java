package io.github.foundationgames.splinecart;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.foundationgames.splinecart.block.TrackGeometry;
import io.github.foundationgames.splinecart.block.entity.ClientTrackGeometry;
import io.github.foundationgames.splinecart.block.entity.TrackTiesBlockEntityRenderer;
import io.github.foundationgames.splinecart.config.Config;
import io.github.foundationgames.splinecart.config.ConfigOption;
import io.github.foundationgames.splinecart.util.SUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;

import java.io.IOException;

public class SplinecartClient implements ClientModInitializer {
	public static final Config CONFIG = new Config("splinecart_client",
			() -> FabricLoader.getInstance().getConfigDir()
					.resolve("splinecart").resolve("splinecart_client.properties"));

	public static final ConfigOption.BooleanOption CFG_ROTATE_CAMERA = CONFIG.optBool("rotate_camera", true);
	public static final ConfigOption.BooleanOption CFG_VBOS = CONFIG.optBool("vbos", false);
	public static final ConfigOption.IntOption CFG_TRACK_RESOLUTION = CONFIG.optInt("track_resolution", 3, 1, 16);
	public static final ConfigOption.IntOption CFG_TRACK_RENDER_DISTANCE = CONFIG.optInt("track_render_distance", 8, 4, 32);

	public static final ShaderProgramKey ENTITY_CUTOUT_NO_CULL_UV_TRANSFORMED = registerShader(
			Splinecart.id("core/rendertype_entity_cutout_no_cull_uv_transform"),
			VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
			Defines.EMPTY);

	@Override
	public void onInitializeClient() {
		SUtil.TICK_DELTA = () -> MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);

		try {
			CONFIG.load();
		} catch (IOException e) {
			Splinecart.LOGGER.error("Error loading client config on mod init", e);
		}

		BlockRenderLayerMap.INSTANCE.putBlock(Splinecart.TRACK_TIES, RenderLayer.getCutout());

		BlockEntityRendererFactories.register(Splinecart.TRACK_TIES_BE, TrackTiesBlockEntityRenderer::new);
		EntityRendererRegistry.register(Splinecart.TRACK_FOLLOWER, EmptyEntityRenderer::new);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(
					LiteralArgumentBuilder.<FabricClientCommandSource>literal("splinecartc")
							.then(CONFIG.command(LiteralArgumentBuilder.literal("config"),
									FabricClientCommandSource::sendFeedback))
		));

		HudRenderCallback.EVENT.register(new SplinecartHud());
		TrackGeometry.CONSTRUCTOR = ClientTrackGeometry::new;
	}

	public static ShaderProgramKey registerShader(Identifier id, VertexFormat format, Defines defines) {
		var key = new ShaderProgramKey(id, format, defines);
		ShaderProgramKeys.getAll().add(key);
		return key;
	}

	public static RenderLayer renderLayerEntityCutoutNoCullUvTransform(Identifier texture, float x, float y) {
		return RenderLayer.of(
				"splinecart_entity_cutout_no_cull_uv_transform",
				VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
				VertexFormat.DrawMode.QUADS,
				1536,
				true, false,
				RenderLayer.MultiPhaseParameters.builder()
						.program(new RenderPhase.ShaderProgram(ENTITY_CUTOUT_NO_CULL_UV_TRANSFORMED))
						.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
						.texturing(new RenderPhase.OffsetTexturing(x, y))
						.transparency(RenderLayer.NO_TRANSPARENCY)
						.cull(RenderLayer.DISABLE_CULLING)
						.lightmap(RenderLayer.ENABLE_LIGHTMAP)
						.overlay(RenderLayer.ENABLE_OVERLAY_COLOR)
						.build(false)
		);
	}
}