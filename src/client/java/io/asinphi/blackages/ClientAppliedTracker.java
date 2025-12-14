package io.asinphi.blackages;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientAppliedTracker {

    private static final int MAX_TRACKED = 10_000;

    /**
     * entityId -> avatarId last applied
     */
    private static final Map<Integer, ResourceLocation> appliedByEntityId = new HashMap<>();

    /**
     * UUIDs for which Figura backend refresh should be blocked (thread-safe).
     * This is what the UserData mixin will consult.
     */
    private static final Set<UUID> overriddenPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Returns true if this entity needs (re)applying the avatar.
     */
    public static boolean needsApply(int entityId, ResourceLocation avatarId) {
        ResourceLocation prev = appliedByEntityId.get(entityId);
        return prev == null || !prev.equals(avatarId);
    }

    /**
     * Mark that the avatar was applied to this entity.
     */
    public static void markApplied(int entityId, ResourceLocation avatarId) {
        if (appliedByEntityId.size() > MAX_TRACKED) {
            appliedByEntityId.clear(); // worst-case: causes reapply burst
        }
        appliedByEntityId.put(entityId, avatarId);
    }

    /**
     * Called when an avatar ID is invalidated (overwritten on server).
     * Clears any entities that were using it so they reapply next tick.
     */
    public static void invalidateId(ResourceLocation avatarId) {
        appliedByEntityId.entrySet().removeIf(e -> avatarId.equals(e.getValue()));
    }

    public static void setPlayerOverridden(UUID playerId, boolean overridden) {
        if (playerId == null) return;
        if (overridden) overriddenPlayers.add(playerId);
        else overriddenPlayers.remove(playerId);
    }

    public static boolean isPlayerOverridden(UUID playerId) {
        return playerId != null && overriddenPlayers.contains(playerId);
    }

    public static void clearAll() {
        appliedByEntityId.clear();
        overriddenPlayers.clear();
    }

    private ClientAppliedTracker() {}
}