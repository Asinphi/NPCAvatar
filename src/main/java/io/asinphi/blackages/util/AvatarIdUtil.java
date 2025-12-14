package io.asinphi.blackages.util;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public final class AvatarIdUtil {
    public static final String MODID = "blackages";

    /** Accepts either "blackages:foo" or just "foo" (becomes "blackages:foo"). */
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
                MODID,
                s.toLowerCase(Locale.ROOT)
        );
    }

    private AvatarIdUtil() {}
}
