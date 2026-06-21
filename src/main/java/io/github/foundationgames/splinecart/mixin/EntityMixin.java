package io.github.foundationgames.splinecart.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import io.github.foundationgames.splinecart.util.SUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "setPos(DDD)V",
            at = @At("TAIL"))
    private void splinecart$getOnTrackIfNecessary(double x, double y, double z, CallbackInfo info) {
        var self = (Entity)(Object)this;
        var world = self.level();
        if (world.isClientSide() || !(self.getType().builtInRegistryHolder().is(Splinecart.CARTS) || self instanceof AbstractMinecart) || self.getVehicle() != null || self.getDeltaMovement().horizontalDistanceSqr() < 0.00005) {
            return;
        }

        var start = self.blockPosition();
        if (world.getBlockEntity(start) instanceof TrackTiesBlockEntity) {
            var follower = TrackFollowerEntity.create(world, self.position(), start, self.getDeltaMovement());
            if (follower != null) {
                world.addFreshEntity(follower);
                self.startRiding(follower, true, false);
            }
        }
    }

    @ModifyReturnValue(method = "calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"))
    private Vec3 splinecart$readjustRotationVec(Vec3 old) {
        var self = (Entity)(Object)this;
        var vehicle = self.getVehicle();
        while (vehicle != null) {
            if (vehicle instanceof TrackFollowerEntity trackFollower) {
                var world = self.level();

                var rotVec = new Vector3d(old.x, old.y, old.z);
                SUtil.BACKWARDS.transform(rotVec);

                if (world.isClientSide()) {
                    var rot = new Quaternionf();
                    float tickDelta = (float) SUtil.TICK_DELTA.getAsDouble();
                    trackFollower.getClientOrientation(rot, tickDelta);
                    rot.transform(rotVec);
                } else {
                    trackFollower.getServerBasis().transform(rotVec);
                }

                return new Vec3(rotVec.x(), rotVec.y(), rotVec.z());
            }

            vehicle = vehicle.getVehicle();
        }

        return old;
    }

    @ModifyReturnValue(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"))
    private Vec3 splinecart$readjustCameraPos(Vec3 old, float tickDelta) {
        var self = (Entity)(Object)this;
        var vehicle = self.getVehicle();
        while (vehicle != null) {
            if (vehicle instanceof TrackFollowerEntity trackFollower) {
                var world = self.level();
                var diff = self.position().add(0, self.getEyeHeight(), 0).subtract(trackFollower.position());
                var camPos = new Vector3d(diff.x, diff.y, diff.z);
                if (world.isClientSide()) {
                    var rot = new Quaternionf();
                    trackFollower.getClientOrientation(rot, tickDelta);
                    rot.transform(camPos);

                    return new Vec3(camPos.x(), camPos.y(), camPos.z()).add(trackFollower.getLerpedPosition(tickDelta));
                }
            }

            vehicle = vehicle.getVehicle();
        }

        return old;
    }

    @Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", cancellable = true, at = @At("HEAD"))
    private void splinecart$modifySuffocationCheck(CallbackInfoReturnable<Vec3> info) {
        var self = (Entity)(Object)this;
        var vehicle = self.getVehicle();
        while (vehicle != null) {
            if (vehicle instanceof TrackFollowerEntity trackFollower) {
                var world = self.level();
                var diff = new Vec3(self.getX(), self.getEyeY(), self.getZ()).subtract(trackFollower.position());
                var eyePos = new Vector3d(diff.x, diff.y, diff.z);
                if (world.isClientSide()) {
                    var rot = new Quaternionf();
                    trackFollower.getClientOrientation(rot, 0);
                    rot.transform(eyePos);
                } else {
                    trackFollower.getServerBasis().transform(eyePos);
                }

                info.setReturnValue(new Vec3(eyePos.x(), eyePos.y(), eyePos.z()).add(trackFollower.position()));
            }

            vehicle = vehicle.getVehicle();
        }
    }
}
