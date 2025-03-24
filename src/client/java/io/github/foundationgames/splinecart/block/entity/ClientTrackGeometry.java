package io.github.foundationgames.splinecart.block.entity;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.block.TrackGeometry;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUsage;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.util.math.MatrixStack;

import java.util.function.Supplier;

public class ClientTrackGeometry extends TrackGeometry {
    private VertexBuffer vbo = null;

    public ClientTrackGeometry(TrackTiesBlockEntity trackTies) {
        super(trackTies);

        this.needsRebuild = true;
    }

    public boolean render(MatrixStack matrices, TrackRenderer.BufferProvider overlayBuffer,
                          Supplier<RenderLayer> trackLayer, int light, int overlay, int segs,
                          TrackTiesBlockEntity curr, TrackTiesBlockEntity prevE, TrackTiesBlockEntity nextE) {
        if (!SplinecartClient.CFG_VBOS.get()) {
            this.needsRebuild = true;
            return false;
        }

        TrackRenderer.BufferProvider trackBuffer = null;
        VertexBuffer vbo = null;
        if (this.needsRebuild) {
            if (this.vbo != null) {
                this.vbo.close();
                this.vbo = null;
            }

            vbo = new VertexBuffer(GlUsage.STATIC_WRITE);
            trackBuffer = TrackRenderer.vboBuf(vbo, Tessellator.getInstance());

            this.needsRebuild = false;
        }

        var trackTransform = new MatrixStack();

        matrices.push();
        trackTransform.push();
        boolean hasGeo = TrackRenderer.renderTrack(trackTransform.peek(), matrices.peek(), trackBuffer,
                overlayBuffer, overlay, light, segs, curr, prevE, nextE);
        matrices.pop();
        trackTransform.pop();

        if (vbo != null) {
            this.vbo = hasGeo ? vbo : null;
        }

        if (this.vbo != null) {
            var layer = trackLayer.get();
            layer.startDrawing();

            matrices.push();

            var cam = MinecraftClient.getInstance().gameRenderer.getCamera();

            matrices.translate(cam.getPos());
            matrices.multiply(cam.getRotation().invert());
            matrices.translate(cam.getPos().negate());

            matrices.push();
            this.vbo.bind();
            this.vbo.draw(matrices.peek().getPositionMatrix(), RenderSystem.getProjectionMatrix(),
                    MinecraftClient.getInstance().getShaderLoader().getOrCreateProgram(ShaderProgramKeys.RENDERTYPE_ENTITY_CUTOUT_NO_CULL));
            VertexBuffer.unbind();
            matrices.pop();

            matrices.pop();
            layer.endDrawing();
        }

        return true;
    }

    @Override
    public void close() {
        super.close();

        if (this.vbo != null) {
            vbo.close();
        }

        this.vbo = null;
    }
}
