package io.github.foundationgames.splinecart.block.entity;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.block.TrackGeometry;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ChunkSectionPos;
import org.joml.Matrix4f;

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

            vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            trackBuffer = TrackRenderer.vboBuf(vbo, Tessellator.getInstance());

            this.resetBounds();
            var currSec = ChunkSectionPos.from(curr.getPos());
            var nextSec = nextE != null ? ChunkSectionPos.from(nextE.getPos()) : currSec;

            this.minSectionX = Math.min(currSec.getX(), nextSec.getX());
            this.minSectionY = Math.min(currSec.getY(), nextSec.getY());
            this.minSectionZ = Math.min(currSec.getZ(), nextSec.getZ());
            this.maxSectionX = Math.max(currSec.getX(), nextSec.getX());
            this.maxSectionY = Math.max(currSec.getY(), nextSec.getY());
            this.maxSectionZ = Math.max(currSec.getZ(), nextSec.getZ());

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
            var fog = RenderSystem.getShaderFogEnd();
            RenderSystem.setShaderFogEnd(999999999);

            var posMatrix = new Matrix4f().set(RenderSystem.getModelViewMatrix());
            posMatrix.mul(matrices.peek().getPositionMatrix());

            matrices.push();
            this.vbo.bind();
            this.vbo.draw(posMatrix, RenderSystem.getProjectionMatrix(), GameRenderer.getRenderTypeEntityCutoutNoNullZOffsetProgram());
            VertexBuffer.unbind();
            matrices.pop();

            matrices.pop();
            RenderSystem.setShaderFogEnd(fog);
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
