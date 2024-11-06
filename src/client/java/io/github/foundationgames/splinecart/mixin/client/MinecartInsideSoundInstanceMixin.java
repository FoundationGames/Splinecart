package io.github.foundationgames.splinecart.mixin.client;

import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.client.sound.MinecartInsideSoundInstance;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecartInsideSoundInstance.class)
public abstract class MinecartInsideSoundInstanceMixin extends MovingSoundInstance {
    @Shadow @Final private AbstractMinecartEntity minecart;

    protected MinecartInsideSoundInstanceMixin(SoundEvent soundEvent, SoundCategory soundCategory, Random random) {
        super(soundEvent, soundCategory, random);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void splinecart$adjustSoundWhenOnTrack(CallbackInfo info) {
        if (!this.isDone() && minecart.getVehicle() instanceof TrackFollowerEntity trackFollower) {
            float amp = (float) trackFollower.getClientMotion().length();
            this.volume = MathHelper.clamp(amp, 0, 0.75f);
        }
    }
}
