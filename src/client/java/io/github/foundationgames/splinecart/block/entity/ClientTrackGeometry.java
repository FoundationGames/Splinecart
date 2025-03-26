package io.github.foundationgames.splinecart.block.entity;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.block.TrackGeometry;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkSectionPos;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

import java.util.function.Supplier;

public class ClientTrackGeometry extends TrackGeometry {
    private VertexBuffer trackVbo = null;
    private VertexBuffer overlayVbo = null;

    private int lastKnownPowerState = -1;
    private int lastKnownTrackResolution = -1;

    public ClientTrackGeometry(TrackTiesBlockEntity trackTies) {
        super(trackTies);

        this.needsRebuild = true;
    }

    public boolean render(MatrixStack matrices,
                          int light, int overlay, int segs, float olVOffset, Vector3fc olColor,
                          int powerState, int trackResolution,
                          Identifier trackTexture, Identifier overlayTexture,
                          TrackTiesBlockEntity curr, TrackTiesBlockEntity prevE, TrackTiesBlockEntity nextE) {
        if (!SplinecartClient.CFG_VBOS.get()) {
            this.needsRebuild = true;
            return false;
        }

        if (powerState != lastKnownPowerState) {
            this.needsRebuild = true;
        }

        if (trackResolution != lastKnownTrackResolution) {
            this.needsRebuild = true;
        }

        TrackRenderer.BufferProvider trackBuffer = null;
        TrackRenderer.BufferProvider overlayBuffer = null;
        VertexBuffer trackVbo = null;
        VertexBuffer overlayVbo = null;
        if (this.needsRebuild) {
            if (this.trackVbo != null) {
                this.trackVbo.close();
                this.trackVbo = null;
            }
            if (this.overlayVbo != null) {
                this.overlayVbo.close();
                this.overlayVbo = null;
            }

            trackVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            trackBuffer = TrackRenderer.vboBuf(trackVbo, Tessellator.getInstance());

            overlayVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            overlayBuffer = TrackRenderer.vboBuf(overlayVbo, Tessellator.getInstance());

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
        int status = TrackRenderer.renderTrack(trackTransform.peek(), trackTransform.peek(),
                trackBuffer, overlayBuffer,
                overlay, light, segs,
                0, olColor,
                curr, prevE, nextE);
        matrices.pop();
        trackTransform.pop();

        boolean hasTrackGeo = (status & 0b01) > 0;
        boolean hasOverlayGeo = (status & 0b10) > 0;

        if (trackVbo != null) {
            this.trackVbo = hasTrackGeo ? trackVbo : null;
        }
        if (overlayVbo != null) {
            this.overlayVbo = hasOverlayGeo ? overlayVbo : null;
        }

        matrices.push();

        var posMatrix = new Matrix4f().set(RenderSystem.getModelViewMatrix());
        posMatrix.mul(matrices.peek().getPositionMatrix());

        var fog = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogEnd(999999999);

        if (this.trackVbo != null) {
            drawVbo(this.trackVbo, posMatrix, () -> RenderLayer.getEntityCutoutNoCullZOffset(trackTexture),
                    GameRenderer::getRenderTypeEntityCutoutNoNullZOffsetProgram);
        }

        if (this.overlayVbo != null) {
            drawVbo(this.overlayVbo, posMatrix, () -> SplinecartClient.renderLayerEntityCutoutNoCullUvTransform(overlayTexture, 0, olVOffset),
                    SplinecartClient::getProgramEntityCutoutNoCullUvTransform);
        }

        RenderSystem.setShaderFogEnd(fog);

        matrices.pop();

        this.lastKnownPowerState = powerState;
        this.lastKnownTrackResolution = trackResolution;

        return true;
    }

    public static void drawVbo(VertexBuffer vbo, Matrix4f transform, Supplier<RenderLayer> renderLayer, Supplier<ShaderProgram> shader) {
        var layer = renderLayer.get();
        layer.startDrawing();

        var program = shader.get();

        vbo.bind();
        vbo.draw(transform, RenderSystem.getProjectionMatrix(), program);
        VertexBuffer.unbind();

        layer.endDrawing();
    }

    @Override
    public void close() {
        super.close();

        if (this.trackVbo != null) {
            trackVbo.close();
        }
        if (this.overlayVbo != null) {
            overlayVbo.close();
        }

        this.trackVbo = null;
        this.overlayVbo = null;
    }
}
