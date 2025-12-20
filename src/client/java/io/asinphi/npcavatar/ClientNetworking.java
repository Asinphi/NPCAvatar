package io.asinphi.npcavatar;

import io.asinphi.npcavatar.networking.ModPackets;
import io.asinphi.npcavatar.networking.payload.C2SPlayerAvatarBlobPayload;
import io.asinphi.npcavatar.networking.payload.C2SRequestAvatarPayload;
import io.asinphi.npcavatar.networking.payload.C2SRequestEntityBlobPayload;
import io.asinphi.npcavatar.networking.payload.C2SUploadAvatarPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public final class ClientNetworking {

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.S2C_REQUEST_LOCAL_UPLOAD, (payload, context) -> {
            var mc = Minecraft.getInstance();

            if (payload.relativePath().isEmpty()) {
                ClientPlayNetworking.send(new C2SUploadAvatarPayload(payload.avatarId(),
                        FiguraBridge.getLocalPlayerAvatarCompressed()));
                return;
            }

            FiguraBridge.loadLocalAvatarRelativeToFiguraAvatarsAsync(payload.relativePath())
                    .thenAccept(bytes -> {
                        ClientPlayNetworking.send(new C2SUploadAvatarPayload(payload.avatarId(), bytes));
                    })
                    .exceptionally(err -> {
                        mc.execute(() -> {
                            if (mc.player != null) {
                                mc.player.sendSystemMessage(Component.literal(
                                        "[npcavatar] Upload failed: " + (err.getMessage() == null ? err.toString() : err.getMessage())
                                ));
                            }
                        });
                        return null;
                    });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.S2C_UPLOAD_RESULT, (payload, context) -> {
            Minecraft.getInstance().execute(() -> {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    player.sendSystemMessage(Component.literal((payload.ok() ? "✔ " : "✖ ") + payload.message()));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.S2C_AVATAR_DATA, (payload, context) -> {
            Minecraft.getInstance().execute(() -> {
                byte[] data = payload.compressedNbtOrEmpty();
                if (data != null && data.length > 0) {
                    ClientAvatarCache.put(payload.avatarId(), data);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.S2C_AVATAR_INVALIDATE, (payload, context) -> {
            Minecraft.getInstance().execute(() -> {
                ClientAvatarCache.remove(payload.avatarId());
                ClientAppliedTracker.invalidateId(payload.avatarId()); // next class
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.S2C_REQUEST_PLAYER_AVATAR, (payload, context) -> {
            // Run on client thread.
            Minecraft.getInstance().execute(() -> {
                byte[] compressed = FiguraBridge.getLocalPlayerAvatarCompressed();

                // If player has no avatar (or Figura not ready), reply with empty bytes.
                if (compressed == null) compressed = new byte[0];

                ClientPlayNetworking.send(new C2SPlayerAvatarBlobPayload(payload.requestId(), compressed));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.S2C_ENTITY_BLOB, (payload, context) -> {
            Minecraft.getInstance().execute(() -> {
                var mc = Minecraft.getInstance();
                if (mc.level == null) return;

                int entityId = payload.entityId();
                byte[] data = payload.compressedNbt();
                if (data == null || data.length == 0) return;

                var entity = mc.level.getEntity(entityId);
                if (entity == null) return; // not in this client world anymore

                // If we already applied this exact blob hash, don't redo it.
                if (!ClientAppliedTracker.needsApply(entityId, data)) return;

                try {
                    // Decode & apply
                    var nbt = net.minecraft.nbt.NbtIo.readCompressed(
                            new java.io.ByteArrayInputStream(data),
                            net.minecraft.nbt.NbtAccounter.create(8L * 1024L * 1024L)
                    );

                    FiguraBridge.applyToEntity(entity, nbt);

                    // Mark applied using the blob bytes (hashing happens inside the tracker).
//                    ClientAppliedTracker.markApplied(entityId, data); // Clears BLOB_MARKER_ID, causing re-request :(
                } catch (Throwable ignored) {
                    // If decode/apply fails, don't markApplied; client can request again later.
                }
            });
        });
    }

    public static void requestAvatar(ResourceLocation id) {
        ClientPlayNetworking.send(new C2SRequestAvatarPayload(id));
    }

    public static void requestEntityBlob(int entityId) {
        ClientPlayNetworking.send(new C2SRequestEntityBlobPayload(entityId));
    }

    private ClientNetworking() {}
}
