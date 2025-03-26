package io.github.foundationgames.splinecart.block.entity;

import io.github.foundationgames.splinecart.TrackType;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.util.Pose;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.floatprovider.FloatSupplier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Function;
import java.util.function.Supplier;

public enum TrackRenderer {;
    public static int renderTrack(MatrixStack.Entry trackTransform, MatrixStack.Entry overlayTransform,
                                      @Nullable BufferProvider trackBuffer, @Nullable BufferProvider overlayBuffer,
                                      int overlay, int light, int segs, float overlayVOffset, Vector3fc overlayColor,
                                      TrackTiesBlockEntity curr, TrackTiesBlockEntity prevE, TrackTiesBlockEntity nextE) {
        int status = 0;
        var start = curr.pose();

        @Nullable var buffer = trackBuffer != null ? trackBuffer.buffer() : null;
        if (buffer != null) renderExtraTrackEnd(trackTransform, buffer, start, overlay, light, prevE, nextE);

        if (nextE != null) {
            var end = nextE.pose();
            var world = curr.getWorld();

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

    public static boolean renderTrackOverlay(MatrixStack.Entry transform, BufferProvider overlayBuffer,
                                          int overlay, int segs, Pose start, Pose end, World world,
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

    private static void renderExtraTrackEnd(MatrixStack.Entry transform, VertexConsumer buffer, Pose pose,
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

            var matrices = new MatrixStack();
            matrices.push();
            matrices.peek().getNormalMatrix().set(transform.getNormalMatrix());
            matrices.peek().getPositionMatrix().set(transform.getPositionMatrix());

            var tl = pose.translation();
            matrices.translate(tl.x(), tl.y(), tl.z());

            var entry = matrices.peek();
            var posMat = entry.getPositionMatrix();
            var nmlMat = entry.getNormalMatrix();
            for (int x = 0; x < 3; x++) {
                for (int y = 0; y < 3; y++) {
                    posMat.setRowColumn(x, y, (float) pose.basis().getRowColumn(x, y));
                    nmlMat.setRowColumn(x, y, (float) pose.basis().getRowColumn(x, y));
                }
            }

            buffer.vertex(entry, 0.5f, 0, z0).color(TrackTiesBlockEntityRenderer.WHITE).texture(0.25f, v0).overlay(overlay).light(light).normal(entry, 0, 1, 0);
            buffer.vertex(entry, -0.5f, 0, z0).color(TrackTiesBlockEntityRenderer.WHITE).texture(0, v0).overlay(overlay).light(light).normal(entry, 0, 1, 0);

            buffer.vertex(entry, -0.5f, 0, z1).color(TrackTiesBlockEntityRenderer.WHITE).texture(0, v1).overlay(overlay).light(light).normal(entry, 0, 1, 0);
            buffer.vertex(entry, 0.5f, 0, z1).color(TrackTiesBlockEntityRenderer.WHITE).texture(0.25f, v1).overlay(overlay).light(light).normal(entry, 0, 1, 0);

            matrices.pop();
        }
    }

    private static void renderPart(World world, MatrixStack.Entry entry, VertexConsumer buffer, Pose start, Pose end,
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

        var pos0 = new BlockPos(MathHelper.floor(origin0.x()), MathHelper.floor(origin0.y()), MathHelper.floor(origin0.z()));
        var pos1 = new BlockPos(MathHelper.floor(origin1.x()), MathHelper.floor(origin1.y()), MathHelper.floor(origin1.z()));

        int light0 = WorldRenderer.getLightmapCoordinates(world, pos0);
        int light1 = WorldRenderer.getLightmapCoordinates(world, pos1);

        var point = new Vector3f();

        point.set(0.5, 0, 0).mul(basis0).add((float) origin0.x(), (float) origin0.y(), (float) origin0.z());
        buffer.vertex(entry, point).color(color.x(), color.y(), color.z(), 1).texture(u0, v0).overlay(overlay)
                .light(light0).normal(entry, (float) norm0.x(), (float) norm0.y(), (float) norm0.z());
        point.set(-0.5, 0, 0).mul(basis0).add((float) origin0.x(), (float) origin0.y(), (float) origin0.z());
        buffer.vertex(entry, point).color(color.x(), color.y(), color.z(), 1).texture(u1, v0).overlay(overlay)
                .light(light0).normal(entry, (float) norm0.x(), (float) norm0.y(), (float) norm0.z());

        point.set(-0.5, 0, 0).mul(basis1).add((float) origin1.x(), (float) origin1.y(), (float) origin1.z());
        buffer.vertex(entry, point).color(color.x(), color.y(), color.z(), 1).texture(u1, v1).overlay(overlay)
                .light(light1).normal(entry, (float) norm1.x(), (float) norm1.y(), (float) norm1.z());
        point.set(0.5, 0, 0).mul(basis1).add((float) origin1.x(), (float) origin1.y(), (float) origin1.z());
        buffer.vertex(entry, point).color(color.x(), color.y(), color.z(), 1).texture(u0, v1).overlay(overlay)
                .light(light1).normal(entry, (float) norm1.x(), (float) norm1.y(), (float) norm1.z());
    }

    public interface BufferProvider {
        VertexConsumer buffer();

        boolean end();
    }

    public static BufferProvider immediateBuf(VertexConsumerProvider source, Identifier texture, Function<Identifier, RenderLayer> renderType) {
        return new ImmediateBufferProvider(source, texture, renderType);
    }

    public static BufferProvider vboBuf(VertexBuffer vbo, Tessellator tessellator) {
        return new VboBufferProvider(vbo, tessellator);
    }

    public record ImmediateBufferProvider(VertexConsumerProvider source, Identifier texture, Function<Identifier, RenderLayer> renderType) implements BufferProvider {
        @Override
        public VertexConsumer buffer() {
            return source.getBuffer(renderType().apply(texture()));
        }

        @Override
        public boolean end() {
            return true;
        }
    }

    public static class VboBufferProvider implements BufferProvider {
        public final VertexBuffer vbo;
        public final Tessellator tessellator;
        private BufferBuilder buffer = null;

        public VboBufferProvider(VertexBuffer vbo, Tessellator tessellator) {
            this.vbo = vbo;
            this.tessellator = tessellator;
        }

        @Override
        public VertexConsumer buffer() {
            this.buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            return this.buffer;
        }

        @Override
        public boolean end() {
            if (buffer != null) {
                var built = buffer.endNullable();

                if (built != null) {
                    vbo.bind();
                    vbo.upload(built);
                    VertexBuffer.unbind();

                    return true;
                }
            }

            return false;
        }
    }
}
