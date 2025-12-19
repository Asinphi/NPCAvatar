package io.asinphi.npcavatar.util;

import net.minecraft.server.level.ServerPlayer;

public final class PermissionUtil {

    /**
     * Returns true if the player is allowed. If fabric-permissions-api is installed,
     * checks the node; otherwise falls back to op level.
     */
    public static boolean check(ServerPlayer player, String node, int fallbackOpLevel) {
        // Fallback first
        boolean fallback = player.hasPermissions(fallbackOpLevel);

        try {
            // me.lucko.fabric.api.permissions.v0.Permissions.check(player, node, fallbackOpLevel)
            Class<?> perms = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            var method = perms.getMethod("check", net.minecraft.commands.CommandSourceStack.class, String.class, int.class);

            // Permissions.check(player.createCommandSourceStack(), node, fallbackOpLevel)
            Object source = player.createCommandSourceStack();
            Object result = method.invoke(null, source, node, fallbackOpLevel);
            return (boolean) result;
        } catch (Throwable ignored) {
            // Permission API not installed or signature mismatch → use vanilla fallback
            return fallback;
        }
    }

    private PermissionUtil() {}
}
