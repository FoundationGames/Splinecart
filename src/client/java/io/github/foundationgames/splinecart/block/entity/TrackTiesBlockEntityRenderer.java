package io.github.foundationgames.splinecart.block.entity;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.util.Pose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class TrackTiesBlockEntityRenderer implements BlockEntityRenderer<TrackTiesBlockEntity> {
    public static final int WHITE = 0xFFFFFFFF;
    public static final Identifier TRACK_TEXTURE = Splinecart.id("textures/track.png");
    public static final Identifier POSE_TEXTURE_DEBUG = Splinecart.id("textures/debug.png");

    public TrackTiesBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(TrackTiesBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        var start = entity.pose();
        var pos = entity.getPos();

        if (MinecraftClient.getInstance().getDebugHud().shouldShowDebugHud()) {
            matrices.push();

            matrices.translate(0.5, 0.5, 0.5);
            var buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(POSE_TEXTURE_DEBUG));
            renderDebug(start, matrices.peek(), buffer);

            matrices.pop();
        }

        var nextE = entity.next();
        if (nextE == null) return;
        var end = nextE.pose();
        var world = entity.getWorld();

        matrices.push();

        matrices.translate(-pos.getX(), -pos.getY(), -pos.getZ());
        var buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(getTexture()));

        int segs = 2 * Math.max((int) start.translation().distance(end.translation()), 3);
        var basis = new Matrix3d(start.basis());

        for (int i = 0; i < segs; i++) {
            double t0 = (double)i / segs;
            double t1 = (double)(i + 1) / segs;

            renderPart(world, matrices.peek(), buffer, start, end, t0, t1, basis, overlay);
        }

        matrices.pop();
    }

    protected Identifier getTexture() {
        return TRACK_TEXTURE;
    }

    @Override
    public boolean rendersOutsideBoundingBox(TrackTiesBlockEntity blockEntity) {
        return true;
    }

    private void renderDebug(Pose pose, MatrixStack.Entry entry, VertexConsumer buffer) {
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

    private void renderPart(World world, MatrixStack.Entry entry, VertexConsumer buffer, Pose start, Pose end, double t0, double t1, Matrix3d basis0, int overlay) {
        var origin0 = new Vector3d();
        start.interpolate(end, t0, origin0, basis0);
        var norm0 = new Vector3d(0, 1, 0).mul(basis0);

        var origin1 = new Vector3d();
        var basis1 = new Matrix3d(basis0);
        start.interpolate(end, t1, origin1, basis1);
        var norm1 = new Vector3d(0, 1, 0).mul(basis1);

        var pos0 = new BlockPos(MathHelper.floor(origin0.x()), MathHelper.floor(origin0.y()), MathHelper.floor(origin0.z()));
        var pos1 = new BlockPos(MathHelper.floor(origin1.x()), MathHelper.floor(origin1.y()), MathHelper.floor(origin1.z()));

        int light0 = WorldRenderer.getLightmapCoordinates(world, pos0);
        int light1 = WorldRenderer.getLightmapCoordinates(world, pos1);

        var point = new Vector3f();

        point.set(0.5, 0, 0).mul(basis0).add((float) origin0.x(), (float) origin0.y(), (float) origin0.z());
        buffer.vertex(entry, point).color(WHITE).texture(1, 1).overlay(overlay).light(light0).normal(entry, (float) norm0.x(), (float) norm0.y(), (float) norm0.z());
        point.set(-0.5, 0, 0).mul(basis0).add((float) origin0.x(), (float) origin0.y(), (float) origin0.z());
        buffer.vertex(entry, point).color(WHITE).texture(0, 1).overlay(overlay).light(light0).normal(entry, (float) norm0.x(), (float) norm0.y(), (float) norm0.z());

        point.set(-0.5, 0, 0).mul(basis1).add((float) origin1.x(), (float) origin1.y(), (float) origin1.z());
        buffer.vertex(entry, point).color(WHITE).texture(0, 0).overlay(overlay).light(light1).normal(entry, (float) norm1.x(), (float) norm1.y(), (float) norm1.z());
        point.set(0.5, 0, 0).mul(basis1).add((float) origin1.x(), (float) origin1.y(), (float) origin1.z());
        buffer.vertex(entry, point).color(WHITE).texture(1, 0).overlay(overlay).light(light1).normal(entry, (float) norm1.x(), (float) norm1.y(), (float) norm1.z());
    }
}
