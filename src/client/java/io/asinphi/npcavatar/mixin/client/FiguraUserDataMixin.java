package io.asinphi.npcavatar.mixin.client;

import io.asinphi.npcavatar.ClientAppliedTracker;
import net.minecraft.nbt.CompoundTag;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.UserData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(UserData.class)
public abstract class FiguraUserDataMixin {

    @Final
    @Shadow
    public UUID id;

    @Shadow
    public Avatar getMainAvatar() {
        throw new AssertionError();
    }

    @Inject(method = "loadAvatar", at = @At("HEAD"), cancellable = true)
    private void npcavatar$blockBackendRefresh(
            CompoundTag nbt, CallbackInfo ci
    ) {
        // Prevent player from changing avatar locally
        if (ClientAppliedTracker.isPlayerOverridden(this.id, nbt)) {
            ci.cancel();
        }
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void npcavatar$blockClear(
            CallbackInfo ci
    ) {
        // Prevent clearing avatars other than one from the avatar-id attachment
        Avatar mainAvatar = getMainAvatar();
        if (mainAvatar != null &&
                mainAvatar.nbt.equals(ClientAppliedTracker.getPlayerOverridingNbt(id))) {
            ci.cancel();
        }
    }
}
