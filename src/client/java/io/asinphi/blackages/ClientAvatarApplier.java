package io.asinphi.blackages;

import io.asinphi.blackages.attachment.ModAttachments;
import io.asinphi.blackages.avatar.ClientAvatarCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

@Environment(EnvType.CLIENT)
public final class ClientAvatarApplier {

    private static int tickCounter = 0;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientAvatarApplier::onEndTick);
    }

    private static void onEndTick(Minecraft mc) {
        if (mc.level == null) return;

        // Don’t do this every tick; 10 ticks = twice a second.
        if ((tickCounter++ % 10) != 0) return;

        for (Entity e : mc.level.entitiesForRendering()) {
            ResourceLocation avatarId = e.getAttached(ModAttachments.AVATAR_ID);

            // If server cleared it (or missing), explicitly un-apply Figura avatar.
            if (avatarId == null || avatarId.equals(ModAttachments.NONE)) {
                // Only do work if we previously applied something.
                if (ClientAppliedTracker.needsApply(e.getId(), ModAttachments.NONE)) {
                    try {
                        FiguraBridge.clearEntityAvatar(e);
                    } catch (Throwable ignored) {}

                    // Mark as "none applied" so we don't clear every time.
                    ClientAppliedTracker.markApplied(e.getId(), ModAttachments.NONE);
                }
                continue;
            }

            // If we don't have the avatar data yet, request it once.
            if (!ClientAvatarCache.has(avatarId)) {
                if (ClientAvatarCache.markRequested(avatarId)) {
                    ClientNetworking.requestAvatar(avatarId);
                }
                continue;
            }

            // Avoid reapplying every run.
            if (!ClientAppliedTracker.needsApply(e.getId(), avatarId)) continue;

            // Decode and apply.
            CompoundTag nbt = ClientAvatarCache.decompress(avatarId);
            if (nbt == null) {
                // decompress() already drops invalid cache; next tick it'll request again
                continue;
            }

            try {
                FiguraBridge.applyToEntity(e, nbt);
                ClientAppliedTracker.markApplied(e.getId(), avatarId);
            } catch (Throwable t) {
                // If Figura throws, don't permanently mark as applied.
                // You might optionally log here.
            }
        }
    }

    private ClientAvatarApplier() {}
}
