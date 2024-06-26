package io.github.foundationgames.splinecart;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.foundationgames.splinecart.block.entity.TrackTiesBlockEntityRenderer;
import io.github.foundationgames.splinecart.config.Config;
import io.github.foundationgames.splinecart.config.ConfigOption;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EmptyEntityRenderer;

import java.io.IOException;

public class SplinecartClient implements ClientModInitializer {
	public static final Config CONFIG = new Config("splinecart_client",
			() -> FabricLoader.getInstance().getConfigDir()
					.resolve("splinecart").resolve("splinecart_client.properties"));

	public static final ConfigOption.BooleanOption CFG_ROTATE_CAMERA = CONFIG.optBool("rotate_camera", true);

	@Override
	public void onInitializeClient() {
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
	}
}