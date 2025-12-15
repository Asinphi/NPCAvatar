package io.asinphi.blackages.mixin.client;

import io.asinphi.blackages.ClientAppliedTracker;
import org.figuramc.figura.avatar.AvatarManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(AvatarManager.class)
public abstract class FiguraAvatarManagerMixin {
    @Inject(method = "clearAvatars", at = @At("HEAD"), cancellable = true)
    private static void blackages$blockClearAvatars(
            UUID id, CallbackInfo ci
    ) {
        // Prevent clearing avatars and re-fetching from back-end
        if (ClientAppliedTracker.isPlayerOverridden(id)) {
            ci.cancel();
        }
    }
}