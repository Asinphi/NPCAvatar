package io.asinphi.npcavatar.util;

import io.asinphi.npcavatar.NPCAvatar;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public final class AvatarIdUtil {
    /** Accepts either "npcavatar:foo" or just "foo" (becomes "npcavatar:foo"). */
    public static ResourceLocation parseWithDefaultNamespace(String input) {
        String s = input.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("id is empty");

        if (s.contains(":")) {
            ResourceLocation rl = ResourceLocation.parse(s);
            return ResourceLocation.fromNamespaceAndPath(
                    rl.getNamespace(),
                    rl.getPath().toLowerCase(Locale.ROOT)
            );
        }

        return ResourceLocation.fromNamespaceAndPath(
                NPCAvatar.MOD_ID,
                s.toLowerCase(Locale.ROOT)
        );
    }

    private AvatarIdUtil() {}
}
