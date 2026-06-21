package io.github.foundationgames.splinecart.mixin.client;

import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import io.github.foundationgames.splinecart.util.SUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.WeakHashMap;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Unique
    private final Map<EntityRenderState, Entity> splinecart$stateToEntity = new WeakHashMap<>();

    @Unique
    private boolean onTrackFollower = false;

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private <E extends Entity> void splinecart$captureEntity(E entity, float partialTick, CallbackInfoReturnable<EntityRenderState> info) {
        splinecart$stateToEntity.put(info.getReturnValue(), entity);
    }

    @Inject(method = "submit",
            at = @At(value = "INVOKE", shift = At.Shift.BEFORE, target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit"))
    private void splinecart$rotateEntitiesOnTrackFollower(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack poseStack, SubmitNodeCollector nodeCollector, CallbackInfo info) {
        Entity entity = splinecart$stateToEntity.get(state);
        if (entity == null || entity instanceof TrackFollowerEntity) return;

        Entity vehicle = entity;
        while (vehicle != null) {
            vehicle = vehicle.getVehicle();

            if (vehicle instanceof TrackFollowerEntity trackFollower) {
                float tickDelta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
                var rotation = new Quaternionf();
                trackFollower.getClientOrientation(rotation, tickDelta);

                poseStack.pushPose();
                onTrackFollower = true;

                // The passenger and its TrackFollower are interpolated independently by
                // vanilla. Their render-time difference therefore contains a timing error
                // proportional to speed; rotating that error as a passenger offset makes
                // the cart visibly swing from side to side.
                var renderOffset = entity.getPosition(tickDelta).subtract(trackFollower.getLerpedPosition(tickDelta));
                var passengerOffset = entity.position().subtract(trackFollower.position());
                poseStack.translate(-renderOffset.x, -renderOffset.y, -renderOffset.z);

                poseStack.mulPose(rotation);

                poseStack.translate(passengerOffset.x, passengerOffset.y, passengerOffset.z);
                poseStack.mulPose(SUtil.BACKWARDS);

                return;
            }
        }
    }

    @Inject(method = "submit",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit"))
    private void splinecart$undoTransform(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack poseStack, SubmitNodeCollector nodeCollector, CallbackInfo info) {
        if (onTrackFollower) {
            onTrackFollower = false;
            poseStack.popPose();
        }
    }
}
