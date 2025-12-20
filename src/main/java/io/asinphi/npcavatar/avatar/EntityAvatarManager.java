package io.asinphi.npcavatar.avatar;

import io.asinphi.npcavatar.attachment.ModAttachments;
import io.asinphi.npcavatar.networking.ServerNetworking;
import io.asinphi.npcavatar.util.AvatarIdUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class EntityAvatarManager {
    public static boolean hasAvatarOverride(Entity target) {
        Boolean hasBlob = target.getAttached(ModAttachments.HAS_AVATAR_BLOB);
        if (Boolean.TRUE.equals(hasBlob))
            return true;
        ResourceLocation avatarId = target.getAttached(ModAttachments.AVATAR_ID);
        return avatarId != null && !avatarId.equals(ModAttachments.NONE);
    }

    public static void applyAvatarId(Entity target, ResourceLocation avatarId) {
        target.setAttached(ModAttachments.AVATAR_ID, avatarId);
        target.setAttached(ModAttachments.HAS_AVATAR_BLOB, false);
        target.setAttached(ModAttachments.AVATAR_BLOB, new byte[0]);
    }

    public static void applyAvatarId(Entity target, String avatarId) {
        applyAvatarId(target, AvatarIdUtil.parseWithDefaultNamespace(avatarId));
    }

    public static void clearAvatarFromEntity(Entity target) {
        target.setAttached(ModAttachments.AVATAR_ID, ModAttachments.NONE);
        target.setAttached(ModAttachments.AVATAR_BLOB, new byte[0]);
        target.setAttached(ModAttachments.HAS_AVATAR_BLOB, false);
    }

    public static int fetchFromPlayer(ServerPlayer sourcePlayer, Collection<? extends Entity> targets) {
        // Clear ID-based avatar immediately; blob will arrive async.
        for (Entity e : targets) {
            e.setAttached(ModAttachments.AVATAR_ID, ModAttachments.NONE);
            e.setAttached(ModAttachments.AVATAR_BLOB, new byte[0]);
        }

        ServerNetworking.requestPlayerAvatarBlob(sourcePlayer, List.copyOf(targets));

        return targets.size();
    }

    public static int copyTo(Entity source, Collection<? extends Entity> targets) {
        ResourceLocation avatarId = source.getAttached(ModAttachments.AVATAR_ID);
        byte[] avatarBlob = source.getAttached(ModAttachments.AVATAR_BLOB);
        Boolean hasAvatarBlob = source.getAttached(ModAttachments.HAS_AVATAR_BLOB);

        if (source instanceof ServerPlayer && (hasAvatarBlob == null || !hasAvatarBlob) && (avatarId == null ||
                avatarId.equals(ModAttachments.NONE))) {
            return fetchFromPlayer((ServerPlayer) source, targets);
        }

        for (Entity e : targets) {
            e.setAttached(ModAttachments.AVATAR_ID, avatarId);
            e.setAttached(ModAttachments.AVATAR_BLOB, avatarBlob);
            e.setAttached(ModAttachments.HAS_AVATAR_BLOB, hasAvatarBlob);
        }

        return targets.size();
    }
}
