package io.asinphi.blackages.networking;

import io.asinphi.blackages.avatar.AvatarStoreState;
import io.asinphi.blackages.networking.payload.S2CAvatarDataPayload;
import io.asinphi.blackages.networking.payload.S2CAvatarInvalidatePayload;
import io.asinphi.blackages.networking.payload.S2CUploadResultPayload;
import io.asinphi.blackages.util.PermissionUtil;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayInputStream;

public final class ServerNetworking {

    private static final int MAX_UPLOAD_BYTES = 2 * 1024 * 1024;

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(
                ModPackets.C2S_UPLOAD_AVATAR, (payload, context) -> {
            ServerPlayer player = context.player();

            if (!PermissionUtil.check(player, "blackages.upload", 2)) {
                context.responseSender().sendPacket(new S2CUploadResultPayload(
                        false, "No permission (blackages.upload)."));
                return;
            }

            byte[] data = payload.compressedNbt();
            if (data.length > MAX_UPLOAD_BYTES) {
                context.responseSender().sendPacket(new S2CUploadResultPayload(
                        false, "Upload too large."));
                return;
            }

            // run on server thread
            context.server().execute(() -> {
                long maxBytes = 8L * 1024L * 1024L; // 8 MiB decoded NBT, tune as needed
                    try {
                    CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(data),
                            NbtAccounter.create(maxBytes));
                    if (tag.isEmpty()) {
                        context.responseSender().sendPacket(new S2CUploadResultPayload(
                                false, "Invalid avatar NBT."));
                        return;
                    }
                } catch (Exception e) {
                    context.responseSender().sendPacket(new S2CUploadResultPayload(
                            false, "Invalid avatar NBT."));
                    return;
                }

                AvatarStoreState.get(player.serverLevel()).put(payload.avatarId(), data);

                // broadcast invalidate
                var inv = new S2CAvatarInvalidatePayload(payload.avatarId());
                for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(p, inv);
                }

                context.responseSender().sendPacket(
                        new S2CUploadResultPayload(true, "Stored avatar '" +
                                payload.avatarId() + "' (" + data.length + " bytes).")
                );
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ModPackets.C2S_REQUEST_AVATAR, (
                payload, context) -> {
            ServerPlayer player = context.player();

            context.server().execute(() -> {
                byte[] data = AvatarStoreState.get(player.serverLevel()).get(payload.avatarId());
                if (data == null) data = new byte[0];
                ServerPlayNetworking.send(player, new S2CAvatarDataPayload(payload.avatarId(), data));
            });
        });
    }

    private ServerNetworking() {}
}
