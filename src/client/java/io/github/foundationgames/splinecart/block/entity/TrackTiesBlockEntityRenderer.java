package io.github.foundationgames.splinecart.block.entity;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.util.Pose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

public class TrackTiesBlockEntityRenderer implements BlockEntityRenderer<TrackTiesBlockEntity> {
    public static final int WHITE = 0xFFFFFFFF;
    public static final Vector3f WHITEF = new Vector3f(1, 1, 1);
    public static final Identifier TRACK_TEXTURE = Splinecart.id("textures/track.png");
    public static final Identifier TRACK_OVERLAY_TEXTURE = Splinecart.id("textures/track_overlay.png");
    public static final Identifier POSE_TEXTURE_DEBUG = Splinecart.id("textures/debug.png");

    public TrackTiesBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(TrackTiesBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        entity.clientTime += tickDelta;

        if (MinecraftClient.getInstance().getDebugHud().shouldShowDebugHud()) {
            matrices.push();

            matrices.translate(0.5, 0.5, 0.5);
            var buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(POSE_TEXTURE_DEBUG));
            renderDebug(entity.pose(), matrices.peek(), buffer);

            matrices.pop();
        }

        int segs = SplinecartClient.CFG_TRACK_RESOLUTION.get() * Math.max((int) entity.estimatedTrackLength(), 2);
        var nextE = entity.next();
        var prevE = entity.prev();
        var overlayBuf = TrackRenderer.immediateBuf(vertexConsumers, getTrackOverlayTexture(), RenderLayer::getEntityCutoutNoCull);

        matrices.push();

        var pos = entity.getPos();
        matrices.translate(-pos.getX(), -pos.getY(), -pos.getZ());

        if (!(entity.geometry instanceof ClientTrackGeometry geo &&
                geo.render(matrices, overlayBuf, () -> RenderLayer.getEntityCutoutNoCull(getTexture()),
                        light, overlay, segs, entity, prevE, nextE)
        )) {
            TrackRenderer.renderTrack(matrices.peek(), matrices.peek(),
                    TrackRenderer.immediateBuf(vertexConsumers, getTexture(), RenderLayer::getEntityCutoutNoCull),
                    overlayBuf,
                    overlay, light, segs, entity, prevE, nextE);
        }

        matrices.pop();
    }

    protected Identifier getTexture() {
        return TRACK_TEXTURE;
    }

    protected Identifier getTrackOverlayTexture() {
        return TRACK_OVERLAY_TEXTURE;
    }

    @Override
    public boolean rendersOutsideBoundingBox(TrackTiesBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return SplinecartClient.CFG_TRACK_RENDER_DISTANCE.get() * 16;
    }

    private static void renderDebug(Pose pose, MatrixStack.Entry entry, VertexConsumer buffer) {
        var posMat = entry.getPositionMatrix();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                posMat.setRowColumn(x, y, (float) pose.basis().getRowColumn(x, y));
            }
        }

        buffer.vertex(entry, 1, 0, 1).color(WHITE).texture(0, 0)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(entry, 0, 1, 0);
        buffer.vertex(entry, 0, 0, 1).color(WHITE).texture(1, 0)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(entry, 0, 1, 0);
        buffer.vertex(entry, 0, 0, 0).color(WHITE).texture(1, 1)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(entry, 0, 1, 0);
        buffer.vertex(entry, 1, 0, 0).color(WHITE).texture(0, 1)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(entry, 0, 1, 0);
    }
}
