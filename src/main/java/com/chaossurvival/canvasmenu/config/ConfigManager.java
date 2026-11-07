package com.chaossurvival.canvasmenu.config;

import com.chaossurvival.canvasmenu.theme.Theme;
import com.chaossurvival.canvasmenu.theme.ThemeLoader;
import com.chaossurvival.canvasmenu.util.Log;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 配置读写（JSON / Gson）。
 *
 * <p>手工构造 {@link JsonObject}，<strong>不依赖 Gson 对 record 的反射支持</strong>，
 * 因此在 MC 自带的任意 Gson 版本下都能工作。配置里不含任何令牌。</p>
 */
public final class ConfigManager {

    private static final String FILE_NAME = "canvasmenu.json";

    private static volatile ModConfig config = ModConfig.defaults();
    private static volatile Theme theme = Theme.defaults();
    private static volatile Path configPath;

    private ConfigManager() {
    }

    /** 初始化：读磁盘配置 → 加载主题 → 应用无障碍适配。 */
    public static void init() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        config = read();
        theme = resolveTheme(config.theme());
        Log.info("Config loaded (theme={}, takeoverInventory={})", config.theme(), config.takeoverInventory());
    }

    /**
     * @return 当前配置快照
     */
    public static ModConfig get() {
        return config;
    }

    /**
     * @return 当前生效主题
     */
    public static Theme theme() {
        return theme;
    }

    /**
     * 保存配置并立即生效（含主题与 reduce-motion 适配）。
     *
     * @param newConfig 新配置
     */
    public static void save(ModConfig newConfig) {
        config = newConfig;
        theme = resolveTheme(newConfig.theme());
        com.chaossurvival.canvasmenu.anim.Animator.setReduceMotion(newConfig.reduceMotion());
        write(newConfig);
    }

    /** 从磁盘重新读取配置并刷新主题。 */
    public static void reload() {
        config = read();
        theme = resolveTheme(config.theme());
    }

    /**
     * 切换主题并持久化。
     *
     * @param themeName 主题名
     * @return 生效后的主题
     */
    public static Theme setTheme(String themeName) {
        save(config.withTheme(themeName));
        return theme;
    }

    private static Theme resolveTheme(String name) {
        Theme loaded = ThemeLoader.loadBuiltin(name);
        return loaded.forHighContrast(config.highContrast());
    }

    private static ModConfig read() {
        ModConfig d = ModConfig.defaults();
        if (configPath == null || !Files.isRegularFile(configPath)) {
            return d;
        }
        try {
            String raw = Files.readString(configPath, StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
            return new ModConfig(
                    str(o, "theme", d.theme()),
                    bool(o, "takeoverInventory", d.takeoverInventory()),
                    bool(o, "takeoverCreative", d.takeoverCreative()),
                    // 更新源固定为出厂值，忽略文件里的改写，防止本地配置投毒
                    ModConfig.TRUSTED_OWNER,
                    ModConfig.TRUSTED_REPO,
                    bool(o, "checkUpdatesOnStartup", d.checkUpdatesOnStartup()),
                    bool(o, "ignorePrerelease", d.ignorePrerelease()),
                    bool(o, "highContrast", d.highContrast()),
                    bool(o, "reduceMotion", d.reduceMotion()),
                    integer(o, "tooltipTicks", d.tooltipTicks()),
                    dbl(o, "uiScale", d.uiScale()),
                    bool(o, "verifySha256", d.verifySha256())
            );
        } catch (IOException | RuntimeException e) {
            Log.warn("Cannot read config, using defaults: {}", e.toString());
            return d;
        }
    }

    private static void write(ModConfig c) {
        if (configPath == null) {
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("theme", c.theme());
        o.addProperty("takeoverInventory", c.takeoverInventory());
        o.addProperty("takeoverCreative", c.takeoverCreative());
        o.addProperty("checkUpdatesOnStartup", c.checkUpdatesOnStartup());
        o.addProperty("ignorePrerelease", c.ignorePrerelease());
        o.addProperty("highContrast", c.highContrast());
        o.addProperty("reduceMotion", c.reduceMotion());
        o.addProperty("tooltipTicks", c.tooltipTicks());
        o.addProperty("uiScale", c.uiScale());
        o.addProperty("verifySha256", c.verifySha256());
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath,
                    new GsonBuilder().setPrettyPrinting().create().toJson(o),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            Log.warn("Cannot write config: {}", e.toString());
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : fallback;
    }

    private static int integer(JsonObject o, String key, int fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : fallback;
    }

    private static double dbl(JsonObject o, String key, double fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : fallback;
    }
}
