package io.github.foundationgames.splinecart.mixin.client;

import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import io.github.foundationgames.splinecart.util.SUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow @Final private Quaternionf rotation;
    @Shadow private Entity entity;
    @Shadow private net.minecraft.world.level.Level level;

    @Inject(method = "alignWithEntity(F)V", at = @At("TAIL"))
    private void splinecart$updateCamPosWhileRiding(float tickDelta, CallbackInfo info) {
        var self = this.entity;
        if (self != null) {
            var vehicle = self.getVehicle();
            if (vehicle != null) {
                var tf = vehicle.getVehicle();
                if (tf instanceof TrackFollowerEntity trackFollower) {
                    var world = this.level;
                    var diff = self.position().add(0, self.getEyeHeight(), 0).subtract(trackFollower.position());
                    var camPos = new Vector3d(diff.x, diff.y, diff.z);
                    if (world.isClientSide()) {
                        var rot = new Quaternionf();
                        trackFollower.getClientOrientation(rot, tickDelta);
                        rot.transform(camPos);

                        if (SUtil.failsSanityCheck(camPos)) {
                            return;
                        }

                        this.setPosition(new Vec3(camPos.x, camPos.y, camPos.z).add(trackFollower.getLerpedPosition(tickDelta)));
                    }
                }
            }
        }
    }

    @Inject(method = "setRotation(FF)V",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 0, target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;", remap = false))
    private void splinecart$updateCamRotationWhileRiding(float yaw, float pitch, CallbackInfo info) {
        var self = this.entity;
        if (self != null) {
            var vehicle = self.getVehicle();
            var tickDelta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            if (vehicle != null) {
                var tf = vehicle.getVehicle();
                if (tf instanceof TrackFollowerEntity trackFollower) {
                    var world = self.level();
                    if (world.isClientSide()) {
                        var rot = new Quaternionf();
                        trackFollower.getClientOrientation(rot, tickDelta);

                        if (SUtil.failsSanityCheck(rot)) {
                            return;
                        }

                        if (SplinecartClient.CFG_ROTATE_CAMERA.get()) {
                            rot.mul(new Quaternionf().rotationY(Mth.DEG_TO_RAD * (90 + vehicle.getViewYRot(tickDelta))).mul(rotation, rotation), rotation);
                        }
                    }
                }
            }
        }
    }
}
