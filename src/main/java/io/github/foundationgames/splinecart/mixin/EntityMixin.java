package io.github.foundationgames.splinecart.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import io.github.foundationgames.splinecart.util.SUtil;
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
        if (world.getBlockEntity(start) instanceof TrackTiesBlockEntity) {
            var follower = TrackFollowerEntity.create(world, self.getPos(), start, self.getVelocity());
            if (follower != null) {
                world.spawnEntity(follower);
                self.startRiding(follower, true);
            }
        }
    }

    @ModifyReturnValue(method = "getRotationVector(FF)Lnet/minecraft/util/math/Vec3d;", at = @At("RETURN"))
    private Vec3d splinecart$readjustRotationVec(Vec3d old) {
        var self = (Entity)(Object)this;
        var vehicle = self.getVehicle();
        while (vehicle != null) {
            if (vehicle instanceof TrackFollowerEntity trackFollower) {
                var world = self.getWorld();

                var rotVec = new Vector3d(old.getX(), old.getY(), old.getZ());
                SUtil.BACKWARDS.transform(rotVec);

                if (world.isClient()) {
                    var rot = new Quaternionf();
                    float tickDelta = (float) SUtil.TICK_DELTA.getAsDouble();
                    trackFollower.getClientOrientation(rot, tickDelta);
                    rot.transform(rotVec);
                } else {
                    trackFollower.getServerBasis().transform(rotVec);
                }

                return new Vec3d(rotVec.x(), rotVec.y(), rotVec.z());
            }

            vehicle = vehicle.getVehicle();
        }

        return old;
    }

    @ModifyReturnValue(method = "getCameraPosVec(F)Lnet/minecraft/util/math/Vec3d;", at = @At("RETURN"))
    private Vec3d splinecart$readjustCameraPos(Vec3d old, float tickDelta) {
        var self = (Entity)(Object)this;
        var vehicle = self.getVehicle();
        while (vehicle != null) {
            if (vehicle instanceof TrackFollowerEntity trackFollower) {
                var world = self.getWorld();
                var diff = self.getPos().add(0, self.getStandingEyeHeight(), 0).subtract(trackFollower.getPos());
                var camPos = new Vector3d(diff.getX(), diff.getY(), diff.getZ());
                if (world.isClient()) {
                    var rot = new Quaternionf();
                    trackFollower.getClientOrientation(rot, tickDelta);
                    rot.transform(camPos);

                    return new Vec3d(camPos.x(), camPos.y(), camPos.z()).add(trackFollower.getLerpedPos(tickDelta));
                }
            }

            vehicle = vehicle.getVehicle();
        }

        return old;
    }

    @Inject(method = "getEyePos()Lnet/minecraft/util/math/Vec3d;", cancellable = true, at = @At("HEAD"))
    private void splinecart$modifySuffocationCheck(CallbackInfoReturnable<Vec3d> info) {
        var self = (Entity)(Object)this;
        var vehicle = self.getVehicle();
        while (vehicle != null) {
            if (vehicle instanceof TrackFollowerEntity trackFollower) {
                var world = self.getWorld();
                var diff = new Vec3d(self.getX(), self.getEyeY(), self.getZ()).subtract(trackFollower.getPos());
                var eyePos = new Vector3d(diff.getX(), diff.getY(), diff.getZ());
                if (world.isClient()) {
                    var rot = new Quaternionf();
                    trackFollower.getClientOrientation(rot, 0);
                    rot.transform(eyePos);
                } else {
                    trackFollower.getServerBasis().transform(eyePos);
                }

                info.setReturnValue(new Vec3d(eyePos.x(), eyePos.y(), eyePos.z()).add(trackFollower.getPos()));
            }

            vehicle = vehicle.getVehicle();
        }
    }
}
