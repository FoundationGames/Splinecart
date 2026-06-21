package io.github.foundationgames.splinecart.block.entity;

import io.github.foundationgames.splinecart.TrackType;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.util.Pose;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.LevelRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Function;

public enum TrackRenderer {;
    public static int renderTrack(PoseStack.Pose trackTransform, PoseStack.Pose overlayTransform,
                                      @Nullable BufferProvider trackBuffer, @Nullable BufferProvider overlayBuffer,
                                      int overlay, int light, int segs, float overlayVOffset, Vector3fc overlayColor,
                                      TrackTiesBlockEntity curr, TrackTiesBlockEntity prevE, TrackTiesBlockEntity nextE) {
        int status = 0;
        var start = curr.pose();

        @Nullable var buffer = trackBuffer != null ? trackBuffer.buffer() : null;
        if (buffer != null) renderExtraTrackEnd(trackTransform, buffer, start, overlay, light, prevE, nextE);

        if (nextE != null) {
            var end = nextE.pose();
            var world = curr.getLevel();

            var trackType = curr.nextType();

            float u0 = trackType.textureU * 0.25f;
            float u1 = u0 + 0.25f;

            var origin = new Vector3d(start.translation());
            var basis = new Matrix3d(start.basis());
            var deriv = new Vector3d(0, 0, 1).mul(start.basis());
            double[] totalDist = {0};

            if (buffer != null) {
                for (int i = 0; i < segs; i++) {
                    double t0 = (double)i / segs;
                    double t1 = (double)(i + 1) / segs;

                    renderPart(world, trackTransform, buffer, start, end, u0, u1, 0, TrackTiesBlockEntityRenderer.WHITEF, t0, t1, totalDist, origin, basis, deriv, overlay);
                }

                status |= trackBuffer.end() ? 0b01 : 0b00;
            }

            if (overlayBuffer != null) {
                status |= renderTrackOverlay(overlayTransform, overlayBuffer,
                        overlay, segs, start, end, world,
                        origin, basis, deriv,
                        overlayVOffset, overlayColor,
                        trackType, curr, nextE)
                ? 0b10 : 0b00;
            }
        } else if (trackBuffer != null) {
            status |= trackBuffer.end() ? 0b01 : 0b00;
        }

        return status;
    }

    public static boolean renderTrackOverlay(PoseStack.Pose transform, BufferProvider overlayBuffer,
                                          int overlay, int segs, Pose start, Pose end, Level world,
                                          Vector3d origin, Matrix3d basis, Vector3d deriv,
                                          float vOffset, Vector3fc color,
                                          TrackType trackType, TrackTiesBlockEntity curr, TrackTiesBlockEntity next) {
        if (trackType.overlay != null) {
            var olBuffer = overlayBuffer.buffer();

            float u0 = trackType.textureU * 0.25f;
            float u1 = u0 + 0.25f;
            double[] totalDist = {0};

            for (int i = 0; i < segs; i++) {
                double t0 = (double)i / segs;
                double t1 = (double)(i + 1) / segs;

                renderPart(world, transform, olBuffer, start, end, u0, u1, vOffset, color, t0, t1, totalDist, origin, basis, deriv, overlay);
            }

            return overlayBuffer.end();
        }

        return false;
    }

    private static void renderExtraTrackEnd(PoseStack.Pose transform, VertexConsumer buffer, Pose pose,
                                            int overlay, int light,
                                            TrackTiesBlockEntity prevE, TrackTiesBlockEntity nextE) {
        if ((prevE == null) ^ (nextE == null)) {
            float z0 = -0.5f;
            float z1 = 0;
            float v0 = 1;
            float v1 = 0.5f;

            if (nextE == null) {
                z0 = 0;
                z1 = 0.5f;
                v0 = 0.5f;
                v1 = 0;
            }

            var matrices = new PoseStack();
            matrices.pushPose();
            matrices.last().normal().set(transform.normal());
            matrices.last().pose().set(transform.pose());

            var tl = pose.translation();
            matrices.translate(tl.x(), tl.y(), tl.z());

            var entry = matrices.last();
            var posMat = entry.pose();
            var nmlMat = entry.normal();
            for (int x = 0; x < 3; x++) {
                for (int y = 0; y < 3; y++) {
                    posMat.setRowColumn(x, y, (float) pose.basis().getRowColumn(x, y));
                    nmlMat.setRowColumn(x, y, (float) pose.basis().getRowColumn(x, y));
                }
            }

            buffer.addVertex(entry, 0.5f, 0, z0).setColor(TrackTiesBlockEntityRenderer.WHITE).setUv(0.25f, v0).setOverlay(overlay).setLight(light).setNormal(entry, 0, 1, 0);
            buffer.addVertex(entry, -0.5f, 0, z0).setColor(TrackTiesBlockEntityRenderer.WHITE).setUv(0, v0).setOverlay(overlay).setLight(light).setNormal(entry, 0, 1, 0);

            buffer.addVertex(entry, -0.5f, 0, z1).setColor(TrackTiesBlockEntityRenderer.WHITE).setUv(0, v1).setOverlay(overlay).setLight(light).setNormal(entry, 0, 1, 0);
            buffer.addVertex(entry, 0.5f, 0, z1).setColor(TrackTiesBlockEntityRenderer.WHITE).setUv(0.25f, v1).setOverlay(overlay).setLight(light).setNormal(entry, 0, 1, 0);

            matrices.popPose();
        }
    }

    private static void renderPart(Level world, PoseStack.Pose entry, VertexConsumer buffer, Pose start, Pose end,
                                   float u0, float u1, float vOffset, Vector3fc color, double t0, double t1, double[] blockProgress,
                                   Vector3d origin0, Matrix3d basis0, Vector3d deriv0, int overlay) {
        start.interpolate(end, t0, origin0, basis0, deriv0);
        var norm0 = new Vector3d(0, 1, 0).mul(basis0);

        var origin1 = new Vector3d(origin0);
        var basis1 = new Matrix3d(basis0);
        var deriv1 = new Vector3d(deriv0);
        start.interpolate(end, t1, origin1, basis1, deriv1);
        var norm1 = new Vector3d(0, 1, 0).mul(basis1);

        float v0 = (float) blockProgress[0];
        while (v0 > 1) v0 -= 1;
        float v1 = v0 + (float) (deriv0.length() * (t1 - t0));

        blockProgress[0] = v1;

        v1 = 1 - v1 + vOffset;
        v0 = 1 - v0 + vOffset;

        var pos0 = new BlockPos(Mth.floor(origin0.x()), Mth.floor(origin0.y()), Mth.floor(origin0.z()));
        var pos1 = new BlockPos(Mth.floor(origin1.x()), Mth.floor(origin1.y()), Mth.floor(origin1.z()));

        int light0 = LevelRenderer.getLightCoords(world, pos0);
        int light1 = LevelRenderer.getLightCoords(world, pos1);

        var point = new Vector3f();

        point.set(0.5, 0, 0).mul(basis0).add((float) origin0.x(), (float) origin0.y(), (float) origin0.z());
        buffer.addVertex(entry, point).setColor(color.x(), color.y(), color.z(), 1).setUv(u0, v0).setOverlay(overlay)
                .setLight(light0).setNormal(entry, (float) norm0.x(), (float) norm0.y(), (float) norm0.z());
        point.set(-0.5, 0, 0).mul(basis0).add((float) origin0.x(), (float) origin0.y(), (float) origin0.z());
        buffer.addVertex(entry, point).setColor(color.x(), color.y(), color.z(), 1).setUv(u1, v0).setOverlay(overlay)
                .setLight(light0).setNormal(entry, (float) norm0.x(), (float) norm0.y(), (float) norm0.z());

        point.set(-0.5, 0, 0).mul(basis1).add((float) origin1.x(), (float) origin1.y(), (float) origin1.z());
        buffer.addVertex(entry, point).setColor(color.x(), color.y(), color.z(), 1).setUv(u1, v1).setOverlay(overlay)
                .setLight(light1).setNormal(entry, (float) norm1.x(), (float) norm1.y(), (float) norm1.z());
        point.set(0.5, 0, 0).mul(basis1).add((float) origin1.x(), (float) origin1.y(), (float) origin1.z());
        buffer.addVertex(entry, point).setColor(color.x(), color.y(), color.z(), 1).setUv(u0, v1).setOverlay(overlay)
                .setLight(light1).setNormal(entry, (float) norm1.x(), (float) norm1.y(), (float) norm1.z());
    }

    public interface BufferProvider {
        VertexConsumer buffer();

        boolean end();
    }

    public static BufferProvider immediateBuf(MultiBufferSource source, Identifier texture, Function<Identifier, RenderType> renderType) {
        return new ImmediateBufferProvider(source, texture, renderType);
    }

    public static BufferProvider vboBuf(Object vbo, Object tessellator) {
        return null;
    }

    public record ImmediateBufferProvider(MultiBufferSource source, Identifier texture, Function<Identifier, RenderType> renderType) implements BufferProvider {
        @Override
        public VertexConsumer buffer() {
            return source.getBuffer(renderType().apply(texture()));
        }

        @Override
        public boolean end() {
            return true;
        }
    }

    public record DirectBufferProvider(VertexConsumer buffer) implements BufferProvider {
        @Override
        public VertexConsumer buffer() {
            return buffer;
        }

        @Override
        public boolean end() {
            return true;
        }
    }
}
