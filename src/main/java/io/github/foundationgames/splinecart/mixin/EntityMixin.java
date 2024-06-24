package io.github.foundationgames.splinecart.mixin;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "setPosition(DDD)V",
            at = @At("TAIL"))
    private void splinecart$getOnTrackIfNecessary(double x, double y, double z, CallbackInfo info) {
        var self = (Entity)(Object)this;
        var world = self.getWorld();
        if (world.isClient() || !self.getType().isIn(Splinecart.CARTS) || self.getVehicle() != null || self.getVelocity().horizontalLengthSquared() < 0.00005) {
            return;
        }

        var start = self.getBlockPos();
        if (world.getBlockEntity(start) instanceof TrackTiesBlockEntity tie) {
            var endE = tie.next();
            if (endE != null) {
                var end = endE.getPos();
                var follower = new TrackFollowerEntity(world, start, end, self.getVelocity());
                world.spawnEntity(follower);
                self.startRiding(follower, true);
            }
        }
    }

    @Inject(method = "getCameraPosVec(F)Lnet/minecraft/util/math/Vec3d;",
            at = @At("RETURN"), cancellable = true)
    private void splinecart$readjustCameraPos(float tickDelta, CallbackInfoReturnable<Vec3d> info) {
        var self = (Entity)(Object)this;
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

                    info.setReturnValue(new Vec3d(camPos.x(), camPos.y(), camPos.z()).add(self.getLerpedPos(tickDelta)));
                }
            }
        }
    }
}
