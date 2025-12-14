package io.asinphi.blackages.mixin.client;

import com.mojang.datafixers.util.Pair;
import io.asinphi.blackages.ClientAppliedTracker;
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

    @Inject(method = "loadData", at = @At("HEAD"), cancellable = true)
    private void blackages$blockBackendRefresh(
            ArrayList<Pair<String, Pair<String, UUID>>> avatars, Pair<BitSet, BitSet> badges, CallbackInfo ci
    ) {
        if (ClientAppliedTracker.isPlayerOverridden(this.id)) {
            ci.cancel();
        }
    }
}
