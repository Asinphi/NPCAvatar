package io.asinphi.npcavatar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientAppliedTracker {

    private static final int MAX_TRACKED = 10_000;

    /**
     * entityId -> avatarId last applied
     */
    private static final Map<Integer, Integer> appliedHashByEntityId = new ConcurrentHashMap<>();

    /**
     * UUIDs for which Figura backend refresh should be blocked (thread-safe).
     * This is what the UserData mixin will consult.
     */
    private static final Map<UUID, CompoundTag> overriddenPlayers = new ConcurrentHashMap<>();

    /**
     * Returns true if this entity needs (re)applying the avatar.
     */
    public static boolean needsApply(int entityId, ResourceLocation avatarId) {
        int desired = hashAvatarId(avatarId);
        Integer prev = appliedHashByEntityId.get(entityId);
        return prev == null || prev != desired;
    }

    public static boolean needsApply(int entityId, byte[] avatarBlob) {
        int desired = hashAvatarBlob(avatarBlob);
        Integer prev = appliedHashByEntityId.get(entityId);
        return prev == null || prev != desired;
    }

    public static boolean needsApply(int entityId, int blobVersion) {
        Integer prev = appliedHashByEntityId.get(entityId);
        return prev == null || prev != blobVersion;
    }

    /**
     * Mark that the avatar was applied to this entity.
     */
    public static void markApplied(int entityId, ResourceLocation avatarId) {
        if (appliedHashByEntityId.size() > MAX_TRACKED) {
            appliedHashByEntityId.clear(); // worst-case: causes reapply burst
        }
        appliedHashByEntityId.put(entityId, hashAvatarId(avatarId));
    }

    public static void markApplied(int entityId, int blobVersion) {
        if (appliedHashByEntityId.size() > MAX_TRACKED) {
            appliedHashByEntityId.clear();
        }
        appliedHashByEntityId.put(entityId, blobVersion);
    }

    /**
     * Called when an avatar ID is invalidated (overwritten on server).
     * Clears any entities that were using it so they reapply next tick.
     */
    public static void invalidateId(ResourceLocation avatarId) {
        int h = hashAvatarId(avatarId);
        if (h == 0) return;
        appliedHashByEntityId.entrySet().removeIf(e -> e.getValue() == h);
    }

    public static void setPlayerOverridden(UUID playerId, CompoundTag nbt) {
        if (playerId == null) return;
        if (nbt != null) overriddenPlayers.put(playerId, nbt);
        else overriddenPlayers.remove(playerId);
    }

    public static CompoundTag getPlayerOverridingNbt(UUID playerId) {
        return overriddenPlayers.get(playerId);
    }

    public static boolean isPlayerOverridden(UUID playerId, CompoundTag nbt) {
        CompoundTag appliedNbt = overriddenPlayers.get(playerId);
        return playerId != null && appliedNbt != null && !appliedNbt.equals(nbt);
    }

    public static void clearAll() {
        appliedHashByEntityId.clear();
        overriddenPlayers.clear();
    }

    private static int hashAvatarId(ResourceLocation avatarId) {
        // 0 means "nothing"
        return avatarId == null ? 0 : avatarId.hashCode();
    }

    private static int hashAvatarBlob(byte[] blob) {
        // 0 means "nothing"
        return (blob == null || blob.length == 0) ? 0 : Arrays.hashCode(blob);
    }

    private ClientAppliedTracker() {}
}
