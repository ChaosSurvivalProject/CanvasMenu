package com.canvasmenu.command;

import com.chaossurvival.canvasmenu.util.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 指令注册表：<strong>JSON 配置驱动</strong>，玩家可自行增删改。
 *
 * <p>读取优先级：</p>
 * <ol>
 *   <li>{@code .minecraft/config/canvasmenu-commands.json} —— 玩家覆盖文件（可选）；</li>
 *   <li>{@code assets/canvasmenu/commands.json} —— jar 内置的出厂配置。</li>
 * </ol>
 *
 * <p>解析策略与主题系统一致：<strong>逐字段回退，任何缺字段、坏色值、坏数组都不会让游戏崩溃</strong>。
 * 坏条目被跳过，id 重复者只保留第一条，最终至少保证有一个「全部」与一个「收藏」虚拟分类。
 * 全部解析在 {@link #reload()} 中完成，结果是不可变快照，可在渲染线程无锁读取。</p>
 */
public final class CommandRegistry {

    /** jar 内置配置路径。 */
    public static final String BUILTIN_RESOURCE = "/assets/canvasmenu/commands.json";
    /** 玩家覆盖文件名（位于 config 目录）。 */
    public static final String OVERRIDE_FILE = "canvasmenu-commands.json";
    /** 「全部」分类显示名。 */
    private static final String ALL_NAME = "全部";
    /** 「收藏」分类显示名。 */
    private static final String FAVORITES_NAME = "收藏";
    /** 兜底主色（青灰）。 */
    private static final int FALLBACK_TINT = 0xFF4FD1C5;

    private static volatile Snapshot snapshot = new Snapshot(List.of(), List.of(), Map.of());

    private CommandRegistry() {
    }

    /** 不可变快照。 */
    private record Snapshot(List<CommandCategory> categories,
                            List<CommandEntry> commands,
                            Map<String, CommandCategory> byId) {
    }

    /**
     * 初始化 / 重新加载。模组启动时调用一次；玩家改完 JSON 后可在设置页再次调用。
     *
     * @return 载入的指令条数
     */
    public static int reload() {
        String json = readOverride().orElseGet(CommandRegistry::readBuiltin);
        Snapshot parsed = parse(json);
        snapshot = parsed;
        Log.info("CommandRegistry loaded: {} commands / {} categories",
                parsed.commands().size(), parsed.categories().size());
        return parsed.commands().size();
    }

    /** @return 全部指令（不可变） */
    public static List<CommandEntry> all() {
        return snapshot.commands();
    }

    /** @return 全部分类（不可变，含「全部」「收藏」虚拟分类） */
    public static List<CommandCategory> categories() {
        return snapshot.categories();
    }

    /**
     * @param id 分类 id
     * @return 分类，未知返回 {@link Optional#empty()}
     */
    public static Optional<CommandCategory> categoryById(String id) {
        return Optional.ofNullable(snapshot.byId().get(id));
    }

    /**
     * @param id 指令 id
     * @return 指令，未知返回 {@link Optional#empty()}
     */
    public static Optional<CommandEntry> byId(String id) {
        for (CommandEntry entry : snapshot.commands()) {
            if (entry.id().equals(id)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** @return 指令总数 */
    public static int count() {
        return snapshot.commands().size();
    }

    /**
     * 过滤：分类 + 搜索词 + 收藏。纯函数，方便单元测试。
     *
     * @param category    分类 id（{@code all} / {@code favorites} / 具体分类 / {@code null}）
     * @param query       搜索词
     * @param favoriteIds 已收藏的指令 id 集合
     * @return 可见指令列表（保持 JSON 中的原始顺序）
     */
    public static List<CommandEntry> filter(String category, String query, Set<String> favoriteIds) {
        Set<String> favorites = favoriteIds == null ? Set.of() : favoriteIds;
        String cat = (category == null || category.isBlank()) ? CommandCategory.ALL : category;
        boolean onlyFavorites = CommandCategory.FAVORITES.equals(cat);
        boolean anyCategory = CommandCategory.ALL.equals(cat) || onlyFavorites;

        List<CommandEntry> result = new ArrayList<>();
        for (CommandEntry entry : snapshot.commands()) {
            if (onlyFavorites && !favorites.contains(entry.id())) {
                continue;
            }
            if (!anyCategory && !cat.equals(entry.category())) {
                continue;
            }
            if (!entry.matches(query)) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 读取
    // ------------------------------------------------------------------

    private static Optional<String> readOverride() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Path file = configDir == null ? null : configDir.resolve(OVERRIDE_FILE);
            if (file != null && Files.isRegularFile(file)) {
                return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            Log.debug("Command override unreadable, using builtin: {}", e.toString());
        }
        return Optional.empty();
    }

    private static String readBuiltin() {
        try (InputStream in = CommandRegistry.class.getResourceAsStream(BUILTIN_RESOURCE)) {
            if (in == null) {
                return "";
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[512];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                }
                return sb.toString();
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Cannot read builtin commands.json: {}", e.toString());
            return "";
        }
    }

    // ------------------------------------------------------------------
    // 解析
    // ------------------------------------------------------------------

    private static Snapshot parse(String json) {
        List<CommandCategory> categories = new ArrayList<>();
        List<CommandEntry> commands = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Set<String> seenIcons = new HashSet<>();

        try {
            JsonElement rootElement = JsonParser.parseString(json == null ? "" : json);
            if (rootElement.isJsonObject()) {
                JsonObject root = rootElement.getAsJsonObject();
                parseCategories(root.getAsJsonArray("categories"), categories);
                parseCommands(root.getAsJsonArray("commands"), commands, seenIds, seenIcons);
            }
        } catch (RuntimeException e) {
            Log.warn("Invalid commands.json, falling back to empty registry: {}", e.toString());
        }

        // 保证两个虚拟分类一定存在
        ensureCategory(categories, CommandCategory.ALL, ALL_NAME, "✦", 0xFF8FA0B4, true);
        ensureCategory(categories, CommandCategory.FAVORITES, FAVORITES_NAME, "★", 0xFFF2C14E, true);

        Map<String, CommandCategory> byId = new LinkedHashMap<>();
        for (CommandCategory category : categories) {
            byId.putIfAbsent(category.id(), category);
        }
        return new Snapshot(List.copyOf(categories), List.copyOf(commands), Map.copyOf(byId));
    }

    private static void ensureCategory(List<CommandCategory> categories, String id, String name,
                                       String glyph, int tint, boolean virtual) {
        for (CommandCategory existing : categories) {
            if (existing.id().equals(id)) {
                return;
            }
        }
        // 「全部」放最前，「收藏」放最后，其余按 JSON 顺序
        if (CommandCategory.ALL.equals(id)) {
            categories.add(0, new CommandCategory(id, name, glyph, tint, "", virtual));
        } else {
            categories.add(new CommandCategory(id, name, glyph, tint, "", virtual));
        }
    }

    private static void parseCategories(JsonArray array, List<CommandCategory> out) {
        if (array == null) {
            return;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject o = element.getAsJsonObject();
            String id = string(o, "id", "");
            if (id.isBlank()) {
                continue;
            }
            boolean duplicate = out.stream().anyMatch(c -> c.id().equals(id));
            if (duplicate) {
                continue;
            }
            out.add(new CommandCategory(
                    id,
                    string(o, "name", id),
                    string(o, "glyph", "•"),
                    color(o, "tint", FALLBACK_TINT),
                    string(o, "icon", ""),
                    bool(o, "virtual", false)));
        }
    }

    private static void parseCommands(JsonArray array, List<CommandEntry> out,
                                      Set<String> seenIds, Set<String> seenIcons) {
        if (array == null) {
            return;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject o = element.getAsJsonObject();
            String id = string(o, "id", "");
            String command = string(o, "command", "");
            // 没有 id 或指令正文的条目没有意义，直接跳过
            if (id.isBlank() || command.isBlank() || !seenIds.add(id)) {
                continue;
            }
            String icon = string(o, "icon", "");
            if (!icon.isBlank() && !seenIcons.add(icon)) {
                Log.debug("Duplicate command icon '{}' (id={}), keeping both", icon, id);
            }
            out.add(new CommandEntry(
                    id,
                    string(o, "category", "misc"),
                    icon,
                    string(o, "label", id),
                    string(o, "description", ""),
                    command,
                    strings(o, "keywords"),
                    color(o, "tint", FALLBACK_TINT),
                    string(o, "glyph", "")));
        }
    }

    // ------------------------------------------------------------------
    // 小工具：全部带默认值，任何异常都被吞掉
    // ------------------------------------------------------------------

    private static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : fallback;
    }

    private static List<String> strings(JsonObject o, String key) {
        JsonArray array = o.getAsJsonArray(key);
        if (array == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                values.add(element.getAsString());
            }
        }
        return values;
    }

    /** 解析 {@code #RRGGBB} / {@code #AARRGGBB}，与主题系统规则一致。 */
    private static int color(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        String s = e.getAsString().trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        try {
            return switch (s.length()) {
                case 6 -> 0xFF000000 | (int) (Long.parseLong(s, 16) & 0x00FFFFFFL);
                case 8 -> (int) Long.parseLong(s, 16);
                default -> fallback;
            };
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
