package io.asinphi.blackages.config;

import io.asinphi.blackages.BlackAges;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.platform.fabric.FabricServerAudiences;
import net.kyori.adventure.platform.fabric.PlayerLocales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Lang {
    private static final String DEFAULT_LOCALE = "en_us"; // fallback
    private static final Path LANG_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("NPCAvatar").resolve("lang");

    private static volatile FabricServerAudiences adventure;
    private static final Map<UUID, Locale> playerLocales = new ConcurrentHashMap<>();
    // Bundle cache: localeTag -> (key -> raw MiniMessage string)
    private static final Map<String, Map<String, String>> bundles = new ConcurrentHashMap<>();

    public static FabricServerAudiences adventure() {
        var a = adventure;
        if (a == null) throw new IllegalStateException("Attempted to access Adventure without a running server!");
        return a;
    }

    public static void init() {
        // Ensure directory exists + default file exists
        ensureDefaults();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            adventure = FabricServerAudiences.of(server);

            // Preload default bundle
            loadLocale(DEFAULT_LOCALE);

            // Track locale changes for each player :contentReference[oaicite:4]{index=4}
            PlayerLocales.CHANGED_EVENT.register((player, locale) -> {
                if (locale == null) return;

                playerLocales.put(player.getUUID(), locale);

                String tag = toLocaleTag(locale);
                if (!bundles.containsKey(tag)) {
                    loadLocale(tag); // only hits disk once per tag
                }
            });
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            var a = adventure;
            adventure = null;
            playerLocales.clear();
            bundles.clear();
            if (a != null) a.close();
        });
    }

    public static void reload() {
        Set<String> tags = new HashSet<>(bundles.keySet());
        tags.add(DEFAULT_LOCALE);

        for (Locale loc : playerLocales.values()) {
            tags.add(toLocaleTag(loc));
        }

        for (String tag : tags) {
            loadLocale(tag);
        }
    }

    public static void send(ServerPlayer player, String key, TagResolver... placeholders) {
        adventure().player(player.getUUID()).sendMessage(render(player, key, placeholders));
    }

    public static void send(CommandSourceStack source, String key, TagResolver... placeholders) {
        var entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            send(player, key, placeholders);
            return;
        }

        // Console / command block / function: default locale
        adventure().audience(source).sendMessage(render(DEFAULT_LOCALE, key, placeholders));
    }

    /** Render message for player (useful if you need to append/compose). */
    public static Component render(ServerPlayer player, String key, TagResolver... placeholders) {
        return render(localeTagFor(player), key, placeholders);
    }

    /** Render for a specific locale tag (useful for console). */
    public static Component render(String localeTag, String key, TagResolver... placeholders) {
        if (localeTag == null || localeTag.isBlank()) localeTag = DEFAULT_LOCALE;

        String raw = getRaw(localeTag, key);

        String prefixRaw = getRaw(localeTag, "prefix");
        if (prefixRaw == null) prefixRaw = getRaw(DEFAULT_LOCALE, "prefix");

        TagResolver resolver = TagResolver.resolver(
                prefixRaw != null ? Placeholder.parsed("prefix", prefixRaw) : TagResolver.empty(),
                TagResolver.resolver(placeholders)
        );

        if (raw == null) {
            return Component.text("Missing lang key: " + key + " (locale=" + localeTag + ")");
        }

        return MM.deserialize(raw, resolver);
    }

    private static final MiniMessage MM = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolver(StandardTags.defaults()) // includes color/decorations/click/hover, etc.
                    .build())
            .build();

    private static String localeTagFor(ServerPlayer player) {
        Locale loc = playerLocales.get(player.getUUID());
        if (loc == null) return DEFAULT_LOCALE;
        String tag = toLocaleTag(loc);
        return tag.isBlank() ? DEFAULT_LOCALE : tag;
    }

    /**
     * Convert Locale to a Minecraft-ish tag: en_us, pt_br, zh_cn, etc.
     * Locale might be "en_US" -> "en_us"
     */
    private static String toLocaleTag(Locale locale) {
        // Locale could have language + country; Minecraft typically uses language_country
        String lang = locale.getLanguage();
        String country = locale.getCountry();
        if (lang.isBlank()) return DEFAULT_LOCALE;
        if (country.isBlank()) return lang.toLowerCase(Locale.ROOT);
        return (lang + "_" + country).toLowerCase(Locale.ROOT);
    }

    private static String getRaw(String localeTag, String key) {
        if (localeTag == null) localeTag = DEFAULT_LOCALE;

        Map<String, String> bundle = bundles.get(localeTag);
        if (bundle == null) bundle = loadLocale(localeTag);

        String v = bundle.get(key);
        if (v != null) return v;

        // Fallback to default locale
        if (!DEFAULT_LOCALE.equals(localeTag)) {
            Map<String, String> def = bundles.get(DEFAULT_LOCALE);
            if (def == null) def = loadLocale(DEFAULT_LOCALE);
            return def.get(key);
        }

        return null;
    }

    private static Map<String, String> loadLocale(String localeTag) {
        if (localeTag == null || localeTag.isBlank()) localeTag = DEFAULT_LOCALE;

        Path file = LANG_DIR.resolve(localeTag + ".yml");
        if (!Files.exists(file) && !DEFAULT_LOCALE.equals(localeTag)) {
            // If locale file doesn't exist, don't spam disk; store empty bundle so lookups are cheap.
            Map<String, String> empty = Map.of();
            bundles.put(localeTag, empty);
            return empty;
        }

        Map<String, String> out = new HashMap<>();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                Object data = new Yaml().load(r);
                if (data instanceof Map<?, ?> map) {
                    flattenYaml("", map, out);
                }
            } catch (Exception e) {
                // Keep old bundle if load fails
                Map<String, String> prev = bundles.get(localeTag);
                if (prev != null) return prev;
            }
        }

        Map<String, String> unmodifiable = Collections.unmodifiableMap(out);
        bundles.put(localeTag, unmodifiable);
        return unmodifiable;
    }

    private static void flattenYaml(String prefix, Map<?, ?> in, Map<String, String> out) {
        for (var entry : in.entrySet()) {
            String k = String.valueOf(entry.getKey());
            String fullKey = prefix.isEmpty() ? k : (prefix + "." + k);
            Object v = entry.getValue();

            if (v instanceof Map<?, ?> child) {
                flattenYaml(fullKey, child, out);
            } else if (v instanceof List<?> list) {
                // Store list messages as joined lines
                out.put(fullKey, String.join("\n", list.stream().map(String::valueOf).toList()));
            } else if (v != null) {
                out.put(fullKey, String.valueOf(v));
            }
        }
    }

    private static void ensureDefaults() {
        try {
            Files.createDirectories(LANG_DIR);

            Path target = LANG_DIR.resolve(DEFAULT_LOCALE + ".yml");
            if (Files.exists(target)) return;

            // Resource inside your jar:
            // src/main/resources/assets/<modid>/lang/en_us.yml
            String resourcePath = "assets/" + BlackAges.MOD_ID + "/lang/" + DEFAULT_LOCALE + ".yml";

            try (var in = Lang.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new IllegalStateException("Missing bundled lang file: " + resourcePath);
                }

                // Copy it out to config/… on first run
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to init lang directory/files", e);
        }
    }

    private Lang() {}
}
