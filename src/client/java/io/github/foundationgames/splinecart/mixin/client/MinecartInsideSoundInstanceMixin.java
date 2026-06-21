package io.github.foundationgames.splinecart.mixin.client;

import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.client.resources.sounds.RidingEntitySoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RidingEntitySoundInstance.class)
public abstract class MinecartInsideSoundInstanceMixin extends AbstractTickableSoundInstance {
    @Shadow @Final private Entity entity;

    protected MinecartInsideSoundInstanceMixin(SoundEvent soundEvent, SoundSource soundSource, RandomSource randomSource) {
        super(soundEvent, soundSource, randomSource);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void splinecart$adjustSoundWhenOnTrack(CallbackInfo info) {
        if (!this.isStopped() && this.entity instanceof AbstractMinecart minecart && minecart.getVehicle() instanceof TrackFollowerEntity trackFollower) {
            float amp = (float) trackFollower.getClientMotion().length();
            this.volume = Mth.clamp(amp, 0.0f, 0.75f);
        }
    }
}
