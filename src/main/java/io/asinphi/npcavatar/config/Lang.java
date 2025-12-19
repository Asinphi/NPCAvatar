package io.asinphi.npcavatar.config;

import io.asinphi.npcavatar.NPCAvatar;
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
    private static final String GLOBAL_PLACEHOLDERS_ROOT = "global_placeholders";
    private static final MiniMessage MM = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolver(StandardTags.defaults()) // includes color/decorations/click/hover, etc.
                    .build())
            .build();

    private static volatile FabricServerAudiences adventure;
    private static final Map<UUID, Locale> playerLocales = new ConcurrentHashMap<>();
    // Bundle cache: localeTag -> (key -> raw MiniMessage string)
    private static final Map<String, Map<String, String>> bundles = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> globalPlaceholders = new ConcurrentHashMap<>();

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
            globalPlaceholders.clear();
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

    public static void send(CommandSourceStack source, String key, TagResolver... placeholders) {
        var entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            send(player, key, placeholders);
            return;
        }

        // Console / command block / function: default locale
        adventure().audience(source).sendMessage(render(DEFAULT_LOCALE, key, placeholders));
    }

    public static void send(ServerPlayer player, String key, TagResolver... placeholders) {
        adventure().player(player.getUUID()).sendMessage(render(player, key, placeholders));
    }

    public static void sendWithPrefix(ServerPlayer player, String prefixKey, String key, TagResolver... placeholders) {
        String localeTag = localeTagFor(player);

        Component body = render(localeTag, key, placeholders);
        Component prefix = renderPlaceholderAsComponent(localeTag, prefixKey);

        adventure().player(player.getUUID()).sendMessage(prefix.append(body));
    }

    public static void sendWithPrefix(CommandSourceStack source, String prefixKey, String key, TagResolver... placeholders) {
        var entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            sendWithPrefix(player, prefixKey, key, placeholders);
            return;
        }

        String localeTag = DEFAULT_LOCALE;

        Component body = render(localeTag, key, placeholders);
        Component prefix = renderPlaceholderAsComponent(localeTag, prefixKey);

        adventure().audience(source).sendMessage(prefix.append(body));
    }

    public static void sendSuccess(ServerPlayer player, String key, TagResolver... placeholders) {
        sendWithPrefix(player, "prefix_success", key, placeholders);
    }

    public static void sendError(ServerPlayer player, String key, TagResolver... placeholders) {
        sendWithPrefix(player, "prefix_error", key, placeholders);
    }

    public static void sendSuccess(CommandSourceStack source, String key, TagResolver... placeholders) {
        sendWithPrefix(source, "prefix_success", key, placeholders);
    }

    public static void sendError(CommandSourceStack source, String key, TagResolver... placeholders) {
        sendWithPrefix(source, "prefix_error", key, placeholders);
    }

    /** Render message for player (useful if you need to append/compose). */
    public static Component render(ServerPlayer player, String key, TagResolver... placeholders) {
        return render(localeTagFor(player), key, placeholders);
    }

    /** Render for a specific locale tag (useful for console). */
    public static Component render(String localeTag, String key, TagResolver... placeholders) {
        if (localeTag == null || localeTag.isBlank()) localeTag = DEFAULT_LOCALE;

        String raw = getRaw(localeTag, key);

        if (raw == null) {
            return Component.text("Missing lang key: " + key + " (locale=" + localeTag + ")");
        }

        TagResolver resolver = resolverFor(localeTag, placeholders);
        try {
            return MM.deserialize(raw, resolver);
        } catch (Exception e) {
            // Log details so you can find broken MiniMessage/YAML instantly
            NPCAvatar.LOGGER.error("[Lang] Failed to parse MiniMessage. locale={}, key={}, raw={}",
                    localeTag, key, raw, e);

            // Fall back to a safe plain-text message so commands still work
            return Component.text(stripAngleBrackets(raw));
        }
    }

    /**
     * Renders a global placeholder value (like prefix_success) into a Component.
     * If missing, returns empty component.
     */
    private static Component renderPlaceholderAsComponent(String localeTag, String placeholderKey) {
        if (localeTag == null || localeTag.isBlank()) localeTag = DEFAULT_LOCALE;

        if (!globalPlaceholders.containsKey(localeTag)) loadLocale(localeTag);
        if (!globalPlaceholders.containsKey(DEFAULT_LOCALE)) loadLocale(DEFAULT_LOCALE);

        String raw = null;

        Map<String, String> gp = globalPlaceholders.get(localeTag);
        if (gp != null) raw = gp.get(placeholderKey);

        if (raw == null) {
            Map<String, String> def = globalPlaceholders.get(DEFAULT_LOCALE);
            if (def != null) raw = def.get(placeholderKey);
        }

        if (raw == null) return Component.empty();

        // The placeholder itself can reference other placeholders, so use resolverFor(...)
        try {
            return MM.deserialize(raw, resolverFor(localeTag));
        } catch (Exception e) {
            NPCAvatar.LOGGER.error("[Lang] Failed to parse global placeholder. locale={}, placeholderKey={}, raw={}",
                    localeTag, placeholderKey, raw, e);
            return Component.empty();
        }
    }

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
            Map<String, String> empty = Map.of();
            bundles.put(localeTag, empty);
            globalPlaceholders.put(localeTag, Map.of());
            return empty;
        }

        Map<String, String> msgs = new HashMap<>();
        Map<String, String> globals = new HashMap<>();

        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                Object data = new Yaml().load(r);
                if (data instanceof Map<?, ?> map) {
                    // 1) Load global placeholders from the "global_placeholders" subtree (flattened)
                    Object gp = map.get(GLOBAL_PLACEHOLDERS_ROOT);
                    if (gp instanceof Map<?, ?> gpMap) {
                        loadFlatGlobalPlaceholders(gpMap, globals);
                    }

                    // 2) Load messages from everywhere else (flattened), excluding the subtree
                    for (var entry : map.entrySet()) {
                        String k = String.valueOf(entry.getKey());
                        if (GLOBAL_PLACEHOLDERS_ROOT.equals(k)) continue;

                        Object v = entry.getValue();
                        if (v instanceof Map<?, ?> child) {
                            flattenYaml(k, child, msgs);
                        } else if (v instanceof List<?> list) {
                            msgs.put(k, String.join("\n", list.stream().map(String::valueOf).toList()));
                        } else if (v != null) {
                            msgs.put(k, String.valueOf(v));
                        }
                    }
                }
            } catch (Exception e) {
                NPCAvatar.LOGGER.error("[Lang] Failed to load locale file: {} (locale={})",
                        file, localeTag, e);

                Map<String, String> prev = bundles.get(localeTag);
                if (prev != null) return prev;
            }
        }

        Map<String, String> msgsUnmod = Collections.unmodifiableMap(msgs);
        Map<String, String> globalsUnmod = Collections.unmodifiableMap(globals);

        bundles.put(localeTag, msgsUnmod);
        globalPlaceholders.put(localeTag, globalsUnmod);

        return msgsUnmod;
    }

    private static void loadFlatGlobalPlaceholders(Map<?, ?> in, Map<String, String> out) {
        for (var entry : in.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object v = entry.getValue();

            // Flat only: allow scalar or list; reject nested sections
            if (v instanceof Map<?, ?>) {
                NPCAvatar.LOGGER.warn("[Lang] Ignoring nested global_placeholders section '{}'. " +
                        "Global placeholders must be flat keys like 'prefix_error' or 'symbol_arrow'.", key);
                continue;
            }

            if (v instanceof List<?> list) {
                out.put(key, String.join("\n", list.stream().map(String::valueOf).toList()));
                continue;
            }

            if (v != null) {
                out.put(key, String.valueOf(v));
            }
        }
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
            String resourcePath = "assets/" + NPCAvatar.MOD_ID + "/lang/" + DEFAULT_LOCALE + ".yml";

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

    private static TagResolver resolverFor(String localeTag, TagResolver... extra) {
        if (localeTag == null || localeTag.isBlank()) localeTag = DEFAULT_LOCALE;

        // Ensure loaded
        if (!bundles.containsKey(localeTag) || !globalPlaceholders.containsKey(localeTag)) {
            loadLocale(localeTag);
        }

        Map<String, String> gp = globalPlaceholders.get(localeTag);
        if (gp == null) gp = Map.of();

        // Fallback global placeholders from default locale
        Map<String, String> defGp = globalPlaceholders.get(DEFAULT_LOCALE);
        if (defGp == null) {
            loadLocale(DEFAULT_LOCALE);
            defGp = globalPlaceholders.getOrDefault(DEFAULT_LOCALE, Map.of());
        }

        // Build a resolver that provides all global placeholders:
        // - locale-specific overrides win
        // - otherwise fallback to en_us
        TagResolver.Builder b = TagResolver.builder();

        // First add fallback (en_us), then override with locale-specific keys
        for (var e : defGp.entrySet()) {
            addParsedPlaceholderSafely(b, localeTag, e.getKey(), e.getValue(), true);
        }
        for (var e : gp.entrySet()) {
            addParsedPlaceholderSafely(b, localeTag, e.getKey(), e.getValue(), false);
        }


        // Add caller-supplied placeholders last (caller wins)
        b.resolver(TagResolver.resolver(extra));

        return b.build();
    }

    private static void addParsedPlaceholderSafely(
            TagResolver.Builder b,
            String localeTag,
            String key,
            String raw,
            boolean isDefaultLocaleFallback
    ) {
        if (raw == null) return;
        try {
            b.resolver(Placeholder.parsed(key, raw));
        } catch (Exception ex) {
            // This is the missing visibility you need:
            NPCAvatar.LOGGER.error(
                    "[Lang] Invalid global placeholder ({}). locale={}, key={}, raw={}",
                    isDefaultLocaleFallback ? "default-fallback" : "locale",
                    localeTag, key, raw, ex
            );

            // Fail soft: degrade to literal text (no MiniMessage tags)
            b.resolver(Placeholder.unparsed(key, stripAngleBrackets(raw)));
        }
    }


    private static String stripAngleBrackets(String s) {
        if (s == null) return "";
        return s.replace("<", "⟨").replace(">", "⟩");
    }

    private Lang() {}
}
