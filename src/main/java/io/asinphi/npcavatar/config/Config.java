package io.asinphi.npcavatar.config;

import io.asinphi.npcavatar.NPCAvatar;
import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public final class Config {

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir()
                    .resolve("NPCAvatar")
                    .resolve("config.yml");

    // Cached parsed YAML root (populated by reload()).
    private static volatile Map<?, ?> yamlRoot = Map.of();

    private static int configVersion;
    private static int humanizeZombieArms;

    public static int configVersion() {
        return configVersion;
    }

    /**
     * 0 = don't use human arm animations for zombies
     * 1 = only for zombies with an avatar override
     * 2 = use human arm animations for all zombies
     */
    public static int humanizeZombieArms() {
        return humanizeZombieArms;
    }

    public static void initValues() {
        Map<?, ?> map = yamlRoot;

        configVersion = requireInt(map, "config_version");

        humanizeZombieArms = requireInt(map, "humanize_zombie_arms");
        if (humanizeZombieArms < 0 || humanizeZombieArms > 2) {
            throw new IllegalStateException(
                    "humanize_zombie_arms must be 0, 1, or 2 (got " + humanizeZombieArms + ")"
            );
        }
    }

    public static void load() {
        reload();
    }

    /**
     * Reloads config.yml from disk and caches the parsed YAML root.
     * Does NOT apply any defaults in code; the bundled config.yml is authoritative.
     */
    public static void reload() {
        ensureDefaults();
        yamlRoot = loadYamlRoot();
        initValues();
    }

    private static Map<?, ?> loadYamlRoot() {
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            Object data = new Yaml().load(reader);
            if (!(data instanceof Map<?, ?> map)) {
                throw new IllegalStateException("config.yml root must be a YAML map/object.");
            }
            return map;
        } catch (Exception e) {
            NPCAvatar.LOGGER.error("[Config] Failed to read config.yml: {}", CONFIG_PATH, e);
            throw new RuntimeException("Failed to read config.yml: " + CONFIG_PATH, e);
        }
    }

    private static int requireInt(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new IllegalStateException("Config key '" + key + "' must be an integer (got " + v + ")");
    }

    private static void ensureDefaults() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) return;

            // src/main/resources/assets/<modid>/config.yml
            String resourcePath = "assets/" + NPCAvatar.MOD_ID + "/config.yml";

            try (var in = Config.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new IllegalStateException("Missing bundled config.yml at: " + resourcePath);
                }
                Files.copy(in, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }

            NPCAvatar.LOGGER.info("[Config] Created default config.yml at {}", CONFIG_PATH);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize config.yml", e);
        }
    }

    private Config() {}
}
