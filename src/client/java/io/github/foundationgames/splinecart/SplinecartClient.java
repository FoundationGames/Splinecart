package io.github.foundationgames.splinecart;

import io.github.foundationgames.splinecart.block.entity.TrackTiesBlockEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EmptyEntityRenderer;

public class SplinecartClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockRenderLayerMap.INSTANCE.putBlock(Splinecart.TRACK_TIES, RenderLayer.getCutout());

		BlockEntityRendererFactories.register(Splinecart.TRACK_TIES_BE, TrackTiesBlockEntityRenderer::new);
		EntityRendererRegistry.register(Splinecart.TRACK_FOLLOWER, EmptyEntityRenderer::new);
	}
}