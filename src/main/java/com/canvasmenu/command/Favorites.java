package com.canvasmenu.command;

import com.chaossurvival.canvasmenu.util.Log;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 收藏夹：右键贴图卡片加入 / 移出「收藏」分类。
 *
 * <p>持久化到 {@code .minecraft/config/canvasmenu-favorites.json}，格式极简：
 * {@code {"ids":["weather_clear","time_day"]}}。读写全部带异常兜底，
 * 配置目录不可写时退化为「仅本次会话有效」，绝不抛异常打断游戏。</p>
 *
 * <p>内部用并发集合，虚拟线程与渲染线程可同时读写。</p>
 */
public final class Favorites {

    /** 收藏文件名。 */
    public static final String FILE_NAME = "canvasmenu-favorites.json";

    private static final Set<String> IDS = ConcurrentHashMap.newKeySet();
    private static volatile Path path;

    private Favorites() {
    }

    /** 初始化：确定配置路径并载入已有收藏。 */
    public static synchronized void init() {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir();
            path = dir == null ? null : dir.resolve(FILE_NAME);
        } catch (RuntimeException e) {
            path = null;
        }
        load();
    }

    /**
     * 切换收藏状态。
     *
     * @param id 指令 id
     * @return 切换后是否已收藏
     */
    public static boolean toggle(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        boolean added;
        if (IDS.contains(id)) {
            IDS.remove(id);
            added = false;
        } else {
            IDS.add(id);
            added = true;
        }
        save();
        return added;
    }

    /**
     * @param id 指令 id
     * @return 是否已收藏
     */
    public static boolean contains(String id) {
        return id != null && IDS.contains(id);
    }

    /** @return 不可变的收藏 id 快照 */
    public static Set<String> ids() {
        return Collections.unmodifiableSet(Set.copyOf(IDS));
    }

    /** @return 收藏数量 */
    public static int size() {
        return IDS.size();
    }

    private static void load() {
        IDS.clear();
        Path file = path;
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray array = root.getAsJsonArray("ids");
            if (array != null) {
                for (JsonElement element : array) {
                    if (element.isJsonPrimitive()) {
                        String id = element.getAsString();
                        // 只保留注册表里仍然存在的 id，避免玩家删掉指令后留下幽灵收藏
                        if (CommandRegistry.byId(id).isPresent()) {
                            IDS.add(id);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.debug("Cannot read favorites, starting empty: {}", e.toString());
        }
    }

    private static synchronized void save() {
        Path file = path;
        if (file == null) {
            return;
        }
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (String id : IDS) {
            array.add(id);
        }
        root.add("ids", array);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file,
                    new GsonBuilder().setPrettyPrinting().create().toJson(root),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException | RuntimeException e) {
            Log.debug("Cannot persist favorites: {}", e.toString());
        }
    }
}
