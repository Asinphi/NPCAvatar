package io.asinphi.blackages;

import io.asinphi.blackages.avatar.ClientAvatarCache;
import io.asinphi.blackages.networking.ModPackets;
import io.asinphi.blackages.networking.payload.C2SRequestAvatarPayload;
import io.asinphi.blackages.networking.payload.C2SUploadAvatarPayload;
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

            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal(
                            "[blackages] Requested upload " + payload.avatarId() +
                                    " from '" + payload.relativePath() + "'"
                    ));
                }
            });

            FiguraBridge.loadLocalAvatarRelativeToFiguraAvatarsAsync(payload.relativePath())
                    .thenAccept(bytes -> {
                        ClientPlayNetworking.send(new C2SUploadAvatarPayload(payload.avatarId(), bytes));
                        mc.execute(() -> {
                            if (mc.player != null) {
                                mc.player.sendSystemMessage(Component.literal(
                                        "[blackages] Uploading " + payload.avatarId() + " (" + bytes.length + " bytes)…"
                                ));
                            }
                        });
                    })
                    .exceptionally(err -> {
                        mc.execute(() -> {
                            if (mc.player != null) {
                                mc.player.sendSystemMessage(Component.literal(
                                        "[blackages] Upload failed: " + (err.getMessage() == null ? err.toString() : err.getMessage())
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
    }

    public static void requestAvatar(ResourceLocation id) {
        ClientPlayNetworking.send(new C2SRequestAvatarPayload(id));
    }

    private ClientNetworking() {}
}
