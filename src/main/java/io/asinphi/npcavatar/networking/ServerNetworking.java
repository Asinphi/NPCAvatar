package io.asinphi.npcavatar.networking;

import io.asinphi.npcavatar.attachment.ModAttachments;
import io.asinphi.npcavatar.avatar.AvatarStoreState;
import io.asinphi.npcavatar.networking.payload.*;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ServerNetworking {

    private static final int MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
    private static final long PLAYER_AVATAR_TIMEOUT_MS = 10_000; // 10 seconds
    private static final AtomicInteger NEXT_REQ_ID = new AtomicInteger(1);

    // requestId -> targets (UUIDs) and who is allowed to answer (player UUID)
    private static final Map<Integer, PendingAvatarRequest> PENDING_PLAYER_AVATAR = new ConcurrentHashMap<>();

    private record TargetEntityRef(ResourceKey<Level> dimension, int entityId) {}
    private record PendingAvatarRequest(
            UUID requesterPlayerUuid,
            List<TargetEntityRef> targets,
            long createdAtMs
    ) {
        boolean expired() {
            return System.currentTimeMillis() - createdAtMs > PLAYER_AVATAR_TIMEOUT_MS;
        }
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(
                ModPackets.C2S_UPLOAD_AVATAR, (payload, context) -> {
            ServerPlayer player = context.player();

            if (!Permissions.check(player, "npcavatar.upload", 2)) {
                context.responseSender().sendPacket(new S2CUploadResultPayload(
                        false, "No permission (npcavatar.upload)."));
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

        ServerPlayNetworking.registerGlobalReceiver(ModPackets.C2S_PLAYER_AVATAR_BLOB, (payload, context) -> {
            context.server().execute(() -> handlePlayerAvatarBlob(context.player(), payload));
        });

        // Client -> Server: request per-entity avatar blob (unsynced attachment)
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.C2S_REQUEST_ENTITY_BLOB, (payload, context) -> {
            ServerPlayer player = context.player();
            int entityId = payload.entityId();

            context.server().execute(() -> {
                // Only allow requesting entities in the player's current level.
                Entity e = player.serverLevel().getEntity(entityId);
                if (e == null) {
                    // Entity not in this world (or not tracked anymore) -> ignore.
                    return;
                }

                byte[] blob = e.getAttached(ModAttachments.AVATAR_BLOB);
                if (blob == null || blob.length == 0) {
                    // Nothing to send.
                    return;
                }

                // Reply only to the requesting player.
                ServerPlayNetworking.send(player, new S2CEntityBlobPayload(entityId, blob));
            });
        });
    }

    public static int requestPlayerAvatarBlob(ServerPlayer player, List<? extends Entity> targetEnts) {
        int requestId = NEXT_REQ_ID.getAndIncrement();

        List<TargetEntityRef> targetRefs = new ArrayList<>(targetEnts.size());
        for (Entity e : targetEnts) targetRefs.add(new TargetEntityRef(e.level().dimension(), e.getId()));

        PENDING_PLAYER_AVATAR.values().removeIf(PendingAvatarRequest::expired);
        PENDING_PLAYER_AVATAR.put(requestId, new PendingAvatarRequest(player.getUUID(), targetRefs,
                System.currentTimeMillis()));

        // Ask that client to send back their current avatar blob.
        ServerPlayNetworking.send(player, new S2CRequestPlayerAvatarPayload(requestId));
        return requestId;
    }

    private static void handlePlayerAvatarBlob(ServerPlayer sender, C2SPlayerAvatarBlobPayload payload) {
        PendingAvatarRequest pending = PENDING_PLAYER_AVATAR.remove(payload.requestId());
        if (pending == null || pending.expired()) {
            return;
        }

        // Only the intended player may answer this requestId.
        if (!pending.requesterPlayerUuid().equals(sender.getUUID())) {
            return;
        }

        byte[] data = payload.compressedAvatarNbt();
        if (data == null) data = new byte[0];

        // Apply to each target entity if it still exists.
        MinecraftServer server = sender.getServer();
        if (server == null) return;

        for (TargetEntityRef target : pending.targets()) {
            Entity ent = findEntity(server, target);
            if (ent == null) continue;

            // AttachmentType<byte[]> AVATAR_BLOB (persistent + synced)
            ent.setAttached(ModAttachments.AVATAR_BLOB, data);
            ent.setAttached(ModAttachments.HAS_AVATAR_BLOB, true);
        }
    }

    private static Entity findEntity(MinecraftServer server, TargetEntityRef target) {
        ServerLevel level = server.getLevel(target.dimension());
        if (level == null) return null;
        return level.getEntity(target.entityId());
    }

    private ServerNetworking() {}
}
