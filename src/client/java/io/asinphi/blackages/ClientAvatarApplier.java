package io.asinphi.blackages;

import io.asinphi.blackages.attachment.ModAttachments;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.io.ByteArrayInputStream;

@Environment(EnvType.CLIENT)
public final class ClientAvatarApplier {

    private static final long MAX_DECOMPRESSED_NBT_BYTES = 8L * 1024L * 1024L;

    private static int tickCounter = 0;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientAvatarApplier::onEndTick);
    }

    public static void processEntity(Entity e) {
        if (Boolean.TRUE.equals(e.getAttached(ModAttachments.HAS_AVATAR_BLOB))) {
            if (ClientAppliedTracker.needsApply(e.getId(), ModAttachments.BLOB_MARKER_ID)) {
                // Mark immediately to suppress further requests
                ClientAppliedTracker.markApplied(e.getId(), ModAttachments.BLOB_MARKER_ID);

                ClientNetworking.requestEntityBlob(e.getId());
            }
            return;
        }

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
            return;
        }

        // If we don't have the avatar data yet, request it once.
        if (!ClientAvatarCache.has(avatarId)) {
            if (ClientAvatarCache.markRequested(avatarId)) {
                ClientNetworking.requestAvatar(avatarId);
            }
            return;
        }

        // Avoid reapplying every run.
        if (!ClientAppliedTracker.needsApply(e.getId(), avatarId)) return;

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

        // Don’t do this every tick; 10 ticks = twice a second.
        if ((tickCounter++ % 10) != 0) return;

        for (Entity e : mc.level.entitiesForRendering()) {
            processEntity(e);
        }
    }

    private static CompoundTag decompressCompressedNbt(byte[] compressed) {
        try {
            return NbtIo.readCompressed(
                    new ByteArrayInputStream(compressed),
                    NbtAccounter.create(MAX_DECOMPRESSED_NBT_BYTES)
            );
        } catch (Exception e) {
            return null;
        }
    }

    private ClientAvatarApplier() {}
}
