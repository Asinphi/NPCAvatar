package io.asinphi.blackages;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.avatar.UserData;
import org.figuramc.figura.avatar.local.LocalAvatarLoader;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public final class FiguraBridge {

    /**
     * Uses Figura's LocalAvatarLoader to parse a local avatar folder into a CompoundTag.
     *
     * LocalAvatarLoader is async; completion happens when it calls UserData#loadAvatar(tag).
     */
    public static CompletableFuture<CompoundTag> loadLocalAvatarToNbtAsync(Path avatarFolder) {
        if (avatarFolder == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("avatarFolder is null"));
        }

        CapturingUserData target = new CapturingUserData(UUID.randomUUID());

        // Starts Figura's async task. Result is delivered through target.loadAvatar(...)
        LocalAvatarLoader.loadAvatar(avatarFolder, target);

        return target.future;
    }

    public static CompletableFuture<byte[]> loadLocalAvatarRelativeToFiguraAvatarsAsync(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("relativePath is blank"));
        }

        var avatarRoot = FabricLoader.getInstance().getGameDir().resolve("figura").resolve("avatars");
        var input = Path.of(relativePath);

        // Treat absolute paths as absolute, otherwise resolve under figura/avatars
        var avatarPath = input.isAbsolute() ? input : avatarRoot.resolve(input).normalize();

        if (!Files.exists(avatarPath)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Path not found: " + avatarPath));
        }

        return loadLocalAvatarToNbtAsync(avatarPath).thenApply(tag -> {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                NbtIo.writeCompressed(tag, baos);
                return baos.toByteArray();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Returns the local player's currently loaded Figura avatar as COMPRESSED NBT bytes,
     * or null if the player has no avatar loaded.
     */
    public static byte[] getLocalPlayerAvatarCompressed() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return null;

        UUID uuid = player.getUUID();

        Avatar avatar = AvatarManager.getLoadedAvatar(uuid);
        if (avatar == null || avatar.nbt == null) return null;

        return compress(avatar.nbt);
    }

    /**
     * Applies the avatar NBT to an entity using Figura.
     *
     * Note: Figura maintains its own internal map of loaded entity avatars.
     */
    public static void applyToEntity(Entity entity, CompoundTag avatarNbt) {
        if (entity == null || avatarNbt == null) return;

        if (entity instanceof Player) {
            ClientAppliedTracker.setPlayerOverridden(entity.getUUID(), true);
            AvatarManager.setAvatar(entity.getUUID(), avatarNbt);
            return;
        }
        AvatarManager.loadEntityAvatar(entity, avatarNbt);
    }

    @SuppressWarnings("unchecked")
    public static void clearEntityAvatar(Entity entity) {
        if (entity == null) return;

        if (entity instanceof Player player) {
            UUID id = player.getUUID();

            // Stop blocking backend refresh for this player (your mixin will now allow loadData again)
            ClientAppliedTracker.setPlayerOverridden(id, false);

            // Best effort: clear any currently-loaded avatar for that user in Figura.
            try {
                // 1) Try public API first (if exists in this version)
                // Some Figura versions have clearAvatars(UUID)
                try {
                    AvatarManager.clearAvatars(id);
                    return;
                } catch (Throwable ignored) {
                    // fall through to reflection
                }

                // 2) Reflection: LOADED_USERS: Map<UUID, UserData>
                Field fUsers = AvatarManager.class.getDeclaredField("LOADED_USERS");
                fUsers.setAccessible(true);

                Object v = fUsers.get(null);
                if (!(v instanceof Map<?, ?> raw)) return;

                Map<UUID, UserData> users = (Map) raw;
                UserData user = users.get(id);
                if (user == null) return;

                // clear() cleans and removes loaded avatars in UserData
                user.clear();

                // Optional: you can also remove the user entry entirely.
                // This can make Figura rebuild it next refresh, but it's not required.
                // users.remove(id);

            } catch (Throwable ignored) {
                // soft-fail
            }

            return;
        }

        try {
            Field f = AvatarManager.class.getDeclaredField("LOADED_CEM");
            f.setAccessible(true);

            Object value = f.get(null);
            if (!(value instanceof Map<?, ?> map)) return;

            Avatar removed = (Avatar) ((Map<Entity, ?>) map).remove(entity);
            if (removed != null) {
                removed.clean(); // Avatar.clean() exists in Figura
            }
        } catch (Throwable ignored) {
            // If Figura changes internals, we fail "softly" (worst case: avatar won't clear)
        }
    }

    private static byte[] compress(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * UserData implementation that captures the NBT and completes a future.
     * Avoids constructing/keeping Figura Avatar objects beyond what's necessary.
     */
    private static final class CapturingUserData extends UserData {
        private final CompletableFuture<CompoundTag> future = new CompletableFuture<>();

        private CapturingUserData(UUID id) {
            super(id);
        }

        @Override
        public void loadAvatar(CompoundTag nbt) {
            // Defensive copy so caller gets stable data
            future.complete(nbt.copy());
        }
    }

    private FiguraBridge() {}
}
