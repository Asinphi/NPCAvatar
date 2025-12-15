package io.asinphi.blackages.mixin.client;

import com.mojang.datafixers.util.Pair;
import io.asinphi.blackages.ClientAppliedTracker;
import net.minecraft.nbt.CompoundTag;
import org.figuramc.figura.avatar.UserData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.UUID;

@Mixin(UserData.class)
public abstract class FiguraUserDataMixin {

    @Final
    @Shadow
    public UUID id;

    @Inject(method = "loadAvatar", at = @At("HEAD"), cancellable = true)
    private void blackages$blockBackendRefresh(
            CompoundTag nbt, CallbackInfo ci
    ) {
        // Prevent player from changing avatar locally
        if (ClientAppliedTracker.isPlayerOverridden(this.id, nbt)) {
            System.out.println("Cancelling loadAvatar " +
                    nbt.getCompound("metadata").getString("name"));
            ci.cancel();
        } else {
            System.out.println("Loading avatar; didn't cancel " +
                    nbt.getCompound("metadata").getString("name"));
        }
    }
}
