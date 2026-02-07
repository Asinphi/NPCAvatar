package io.asinphi.npcavatar;

import io.asinphi.npcavatar.attachment.ModAttachments;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

@Environment(EnvType.CLIENT)
public final class ClientAvatarApplier {

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientAvatarApplier::onEndTick);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static void processEntity(Entity e) {
        Integer blobVersion = e.getAttached(ModAttachments.AVATAR_BLOB_VERSION);
        if (blobVersion != null && blobVersion >= 0) {
            if (ClientAppliedTracker.needsApply(e.getId(), blobVersion)) {
                // Mark immediately to suppress further requests for this version.
                ClientAppliedTracker.markApplied(e.getId(), blobVersion);

                ClientNetworking.requestEntityBlob(e.getId());
            }
            return;
        }

        ResourceLocation avatarId = e.getAttached(ModAttachments.AVATAR_ID);

        if (avatarId == null) return;

        // If server cleared it, explicitly un-apply Figura avatar.
        if (avatarId.equals(ModAttachments.NONE)) {
            // Only do work if we previously applied something.
            if (ClientAppliedTracker.needsApply(e.getId(), ModAttachments.NONE)) {
                try {
                    FiguraBridge.clearEntityAvatar(e);
                } catch (Throwable ignored) {}

                // Mark as "none applied" so we don't clear every time.
                ClientAppliedTracker.markApplied(e.getId(), ModAttachments.NONE);
            }
            return;
        }

        // Avoid reapplying every run.
        if (!ClientAppliedTracker.needsApply(e.getId(), avatarId)) return;

        // If we don't have the avatar data yet, request it once.
        if (!ClientAvatarCache.has(avatarId)) {
            if (ClientAvatarCache.markRequested(avatarId)) {
                ClientNetworking.requestAvatar(avatarId);
            }
            return;
        }

        // Decode and apply.
        CompoundTag nbt = ClientAvatarCache.decompress(avatarId);
        if (nbt == null) {
            // decompress() already drops invalid cache; next tick it'll request again
            return;
        }

        try {
            FiguraBridge.applyToEntity(e, nbt);
            ClientAppliedTracker.markApplied(e.getId(), avatarId);
        } catch (Throwable t) {
            // If Figura throws, don't permanently mark as applied.
            // Maybe log here.
        }
    }

    private static void onEndTick(Minecraft mc) {
        if (mc.level == null) return;

        for (Entity e : mc.level.entitiesForRendering()) {
            processEntity(e);
        }
    }

    private ClientAvatarApplier() {}
}
