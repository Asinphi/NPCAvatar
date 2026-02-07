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
        Integer blobVersion = target.getAttached(ModAttachments.AVATAR_BLOB_VERSION);
        if (blobVersion != null && blobVersion >= 0)
            return true;
        ResourceLocation avatarId = target.getAttached(ModAttachments.AVATAR_ID);
        return avatarId != null && !avatarId.equals(ModAttachments.NONE);
    }

    public static void applyAvatarId(Entity target, ResourceLocation avatarId) {
        target.setAttached(ModAttachments.AVATAR_ID, avatarId);
        target.setAttached(ModAttachments.AVATAR_BLOB, new byte[0]);
        target.setAttached(ModAttachments.AVATAR_BLOB_VERSION, ModAttachments.NO_BLOB_VERSION);
    }

    public static void applyAvatarId(Entity target, String avatarId) {
        applyAvatarId(target, AvatarIdUtil.parseWithDefaultNamespace(avatarId));
    }

    public static void clearAvatarFromEntity(Entity target) {
        target.setAttached(ModAttachments.AVATAR_ID, ModAttachments.NONE);
        target.setAttached(ModAttachments.AVATAR_BLOB, new byte[0]);
        target.setAttached(ModAttachments.AVATAR_BLOB_VERSION, ModAttachments.NO_BLOB_VERSION);
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
        boolean sourceHasBlob = avatarBlob != null && avatarBlob.length > 0;

        if (source instanceof ServerPlayer && !sourceHasBlob &&
                (avatarId == null || avatarId.equals(ModAttachments.NONE))) {
            return fetchFromPlayer((ServerPlayer) source, targets);
        }

        for (Entity e : targets) {
            e.setAttached(ModAttachments.AVATAR_ID, avatarId);
            e.setAttached(ModAttachments.AVATAR_BLOB, avatarBlob);
            if (sourceHasBlob) {
                e.setAttached(ModAttachments.AVATAR_BLOB_VERSION, bumpBlobVersion(e));
            } else {
                e.setAttached(ModAttachments.AVATAR_BLOB_VERSION, ModAttachments.NO_BLOB_VERSION);
            }
        }

        return targets.size();
    }

    private static int bumpBlobVersion(Entity target) {
        Integer current = target.getAttached(ModAttachments.AVATAR_BLOB_VERSION);
        if (current == null || current < 0 || current == Integer.MAX_VALUE) return 0;
        return current + 1;
    }
}
