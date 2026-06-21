package io.github.foundationgames.splinecart.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.util.SplineSegment;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Lightweight cuboid supports generated directly into the spline render buffer. */
public final class TrackSupportRenderer {
    private static final double SLEEPER_SPACING = 0.5;
    private static final double CONNECTOR_SPACING = 1.0;

    private TrackSupportRenderer() {
    }

    public static void renderSleepers(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int segs,
                                      TrackTiesBlockEntity start, TrackTiesBlockEntity end) {
        var segment = new SplineSegment(start, end);
        if (!segment.isHorizontal()) return;

        renderRegularly(entry, buffer, overlay, segs, segment, SLEEPER_SPACING, (position, basis, derivative, light) -> {
            var forward = derivative.normalize(new Vector3d());
            var up = new Vector3d(0, 1, 0);
            var right = up.cross(forward, new Vector3d()).normalize();
            var center = new Vector3d(position).fma(-0.075, up);
            cuboid(entry, buffer, overlay, light, center, right, up, forward, 1.30, 0.12, 0.22);
        });
    }

    public static void renderConnectors(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int segs,
                                        TrackTiesBlockEntity start, TrackTiesBlockEntity end) {
        var segment = new SplineSegment(start, end);
        if (segment.isHorizontal()) return;

        renderRegularly(entry, buffer, overlay, segs, segment, CONNECTOR_SPACING, (position, basis, derivative, light) -> {
            var right = basis.getColumn(0, new Vector3d()).normalize();
            var up = basis.getColumn(1, new Vector3d()).normalize();
            var forward = derivative.normalize(new Vector3d());

            cuboid(entry, buffer, overlay, light, new Vector3d(position).fma(-0.055, up),
                    right, up, forward, 1.14, 0.10, 0.16);
            cuboid(entry, buffer, overlay, light, new Vector3d(position).fma(0.39, right).fma(0.015, up),
                    right, up, forward, 0.14, 0.16, 0.22);
            cuboid(entry, buffer, overlay, light, new Vector3d(position).fma(-0.39, right).fma(0.015, up),
                    right, up, forward, 0.14, 0.16, 0.22);
        });
    }

    private static void renderRegularly(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int segs,
                                        SplineSegment segment, double preferredSpacing, SupportConsumer consumer) {
        int samples = Math.clamp(segs * 2, 8, 128);
        var cumulative = new double[samples + 1];
        var previous = new Vector3d(segment.start().pose().translation());
        var position = new Vector3d();
        var basis = new Matrix3d();
        var derivative = new Vector3d();

        for (int i = 1; i <= samples; i++) {
            segment.interpolate((double) i / samples, position, basis, derivative);
            cumulative[i] = cumulative[i - 1] + position.distance(previous);
            previous.set(position);
        }

        double length = cumulative[samples];
        if (length < 0.1) return;
        int count = Math.max(1, (int) Math.round(length / preferredSpacing));
        double spacing = length / count;
        int sample = 1;

        for (int i = 0; i < count; i++) {
            double target = (i + 0.5) * spacing;
            while (sample < samples && cumulative[sample] < target) sample++;
            double sectionLength = cumulative[sample] - cumulative[sample - 1];
            double local = sectionLength > 1.0e-8 ? (target - cumulative[sample - 1]) / sectionLength : 0.0;
            double t = (sample - 1 + local) / samples;
            segment.interpolate(t, position, basis, derivative);
            int light = LevelRenderer.getLightCoords(segment.start().getLevel(), BlockPos.containing(position.x(), position.y(), position.z()));
            consumer.render(position, basis, derivative, light);
        }
    }

    private static void cuboid(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int light,
                               Vector3d center, Vector3d right, Vector3d up, Vector3d forward,
                               double width, double height, double depth) {
        double x = width * 0.5;
        double y = height * 0.5;
        double z = depth * 0.5;
        var corners = new Vector3d[8];
        int index = 0;
        for (int yi = -1; yi <= 1; yi += 2) {
            for (int zi = -1; zi <= 1; zi += 2) {
                for (int xi = -1; xi <= 1; xi += 2) {
                    corners[index++] = new Vector3d(center)
                            .fma(xi * x, right).fma(yi * y, up).fma(zi * z, forward);
                }
            }
        }

        face(entry, buffer, overlay, light, corners, 1, 5, 7, 3, right);
        face(entry, buffer, overlay, light, corners, 4, 0, 2, 6, new Vector3d(right).negate());
        face(entry, buffer, overlay, light, corners, 2, 3, 7, 6, up);
        face(entry, buffer, overlay, light, corners, 4, 5, 1, 0, new Vector3d(up).negate());
        face(entry, buffer, overlay, light, corners, 5, 4, 6, 7, forward);
        face(entry, buffer, overlay, light, corners, 0, 1, 3, 2, new Vector3d(forward).negate());
    }

    private static void face(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int light,
                             Vector3d[] corners, int a, int b, int c, int d, Vector3d normal) {
        vertex(entry, buffer, overlay, light, corners[a], normal, 0, 0);
        vertex(entry, buffer, overlay, light, corners[b], normal, 0, 1);
        vertex(entry, buffer, overlay, light, corners[c], normal, 1, 1);
        vertex(entry, buffer, overlay, light, corners[d], normal, 1, 0);
    }

    private static void vertex(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int light,
                               Vector3d point, Vector3d normal, float u, float v) {
        buffer.addVertex(entry, new Vector3f((float) point.x(), (float) point.y(), (float) point.z()))
                .setColor(TrackTiesBlockEntityRenderer.WHITE).setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(entry, (float) normal.x(), (float) normal.y(), (float) normal.z());
    }

    @FunctionalInterface
    private interface SupportConsumer {
        void render(Vector3d position, Matrix3d basis, Vector3d derivative, int light);
    }
}
