package io.github.foundationgames.splinecart.mixin.client;

import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
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
    @Shadow protected abstract void setPos(Vec3d pos);
    @Shadow @Final private Quaternionf rotation;

    @Inject(method = "update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 0, target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V"))
    private void splinecart$updateCamPosWhileRiding(BlockView area, Entity self, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo info) {
        var vehicle = self.getVehicle();
        if (vehicle != null) {
            var tf = vehicle.getVehicle();
            if (tf instanceof TrackFollowerEntity trackFollower) {
                var world = self.getWorld();
                var camPos = new Vector3d(0, self.getStandingEyeHeight(), 0);
                if (world.isClient()) {
                    var rot = new Quaternionf();
                    trackFollower.getClientOrientation(rot, tickDelta);
                    rot.transform(camPos);

                    this.setPos(new Vec3d(camPos.x(), camPos.y(), camPos.z()).add(self.getLerpedPos(tickDelta)));
                    rot.mul(rotation, rotation);
                }
            }
        }
    }
}
