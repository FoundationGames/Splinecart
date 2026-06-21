package io.github.foundationgames.splinecart.block.entity;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.TrackType;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.util.Pose;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import net.minecraft.util.LightCoordsUtil;

import java.util.Set;

public class TrackTiesBlockEntityRenderer implements BlockEntityRenderer<TrackTiesBlockEntity, TrackTiesBlockEntityRenderer.TrackTiesRenderState> {
    public static final int WHITE = 0xFFFFFFFF;
    public static final Vector3f WHITEF = new Vector3f(1, 1, 1);
    public static final Identifier TRACK_TEXTURE = Splinecart.id("textures/track.png");
    public static final Identifier TRACK_OVERLAY_TEXTURE = Splinecart.id("textures/track_overlay.png");
    public static final Identifier POSE_TEXTURE_DEBUG = Splinecart.id("textures/debug.png");
    public static final Identifier SLEEPER_TEXTURE = Identifier.withDefaultNamespace("textures/block/oak_planks.png");
    public static final Identifier CONNECTOR_TEXTURE = Identifier.withDefaultNamespace("textures/block/cauldron_side.png");

    public TrackTiesBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public TrackTiesRenderState createRenderState() {
        return new TrackTiesRenderState();
    }

    @Override
    public void extractRenderState(TrackTiesBlockEntity entity, TrackTiesRenderState state, float partialTick, net.minecraft.world.phys.Vec3 cameraPos, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(entity, state, crumbling);
        state.trackTies = entity;
        state.pose = entity.pose();
        entity.clientTime += partialTick;
        state.clientTime = entity.clientTime;
        state.estimatedTrackLength = entity.estimatedTrackLength();
        state.next = entity.next();
        state.prev = entity.prev();
        state.nextType = entity.nextType();
        state.power = entity.power();
    }

    @Override
    public void submit(TrackTiesRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (Minecraft.getInstance().getDebugOverlay().showDebugScreen()) {
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            nodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(POSE_TEXTURE_DEBUG), (entry, buffer) -> {
                renderDebug(state.pose, entry, buffer);
            });
            poseStack.popPose();
        }

        int trackResolution = SplinecartClient.CFG_TRACK_RESOLUTION.get();
        int segs = trackResolution * Math.max((int) state.estimatedTrackLength, 2);

        poseStack.pushPose();
        var pos = state.blockPos;
        poseStack.translate(-pos.getX(), -pos.getY(), -pos.getZ());

        var overlayColor = new Vector3f(WHITEF);
        float[] overlayVOffset = {0};

        int power = state.power;

        if (state.next != null) {
            var trackType = state.nextType;

            if (trackType.overlay != null) {
                power = Math.max(state.power, state.next.power());
                trackType.overlay.calculateEffects(power, state.clientTime, overlayColor, overlayVOffset);
            }
        }

        // Render main track
        nodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutZOffset(getTexture()), (entry, buffer) -> {
            var trackProvider = new TrackRenderer.DirectBufferProvider(buffer);
            TrackRenderer.renderTrack(entry, entry,
                    trackProvider, null,
                    OverlayTexture.NO_OVERLAY, state.lightCoords, segs,
                    0, overlayColor,
                    state.trackTies, state.prev, state.next);
        });

        if (state.next != null) {
            nodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(SLEEPER_TEXTURE), (entry, buffer) ->
                    TrackSupportRenderer.renderSleepers(entry, buffer, OverlayTexture.NO_OVERLAY, segs, state.trackTies, state.next));
            nodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(CONNECTOR_TEXTURE), (entry, buffer) ->
                    TrackSupportRenderer.renderConnectors(entry, buffer, OverlayTexture.NO_OVERLAY, segs, state.trackTies, state.next));
        }

        // Render overlay
        if (state.next != null && state.nextType.overlay != null) {
            nodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(getTrackOverlayTexture()), (entry, buffer) -> {
                var overlayProvider = new TrackRenderer.DirectBufferProvider(buffer);
                TrackRenderer.renderTrack(entry, entry,
                        null, overlayProvider,
                        OverlayTexture.NO_OVERLAY, state.lightCoords, segs,
                        overlayVOffset[0], overlayColor,
                        state.trackTies, state.prev, state.next);
            });
        }

        poseStack.popPose();
    }

    protected Identifier getTexture() {
        return TRACK_TEXTURE;
    }

    protected Identifier getTrackOverlayTexture() {
        return TRACK_OVERLAY_TEXTURE;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return SplinecartClient.CFG_TRACK_RENDER_DISTANCE.get() * 16;
    }

    private static void renderDebug(Pose pose, PoseStack.Pose entry, VertexConsumer buffer) {
        var posMat = entry.pose();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                posMat.setRowColumn(x, y, (float) pose.basis().getRowColumn(x, y));
            }
        }

        buffer.addVertex(entry, 1, 0, 1).setColor(WHITE).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(entry, 0, 1, 0);
        buffer.addVertex(entry, 0, 0, 1).setColor(WHITE).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(entry, 0, 1, 0);
        buffer.addVertex(entry, 0, 0, 0).setColor(WHITE).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(entry, 0, 1, 0);
        buffer.addVertex(entry, 1, 0, 0).setColor(WHITE).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(entry, 0, 1, 0);
    }

    public static void queueVboRebuildsForChunkUpdate(int sectionX, int sectionY, int sectionZ, Set<BlockEntity> blockEntities) {
        for (var be : blockEntities) if (be instanceof TrackTiesBlockEntity ties) {
            if (ties.geometry.isInChunk(sectionX, sectionY, sectionZ)) {
                ties.geometry.needsRebuild = true;
            }
        }
    }

    public static class TrackTiesRenderState extends BlockEntityRenderState {
        public TrackTiesBlockEntity trackTies;
        public Pose pose;
        public float clientTime;
        public double estimatedTrackLength;
        public TrackTiesBlockEntity next;
        public TrackTiesBlockEntity prev;
        public TrackType nextType;
        public int power;
    }
}
