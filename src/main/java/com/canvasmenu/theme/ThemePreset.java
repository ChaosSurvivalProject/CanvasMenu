package com.canvasmenu.theme;

import com.chaossurvival.canvasmenu.config.ConfigManager;
import com.chaossurvival.canvasmenu.theme.Theme;
import com.chaossurvival.canvasmenu.theme.ThemeLoader;

import java.util.List;

/**
 * 指令中心的三套预设主题。
 *
 * <p>主题文件位于 {@code assets/canvasmenu/themes/*.json}，由
 * {@link ThemeLoader#loadBuiltin(String)} 解析 —— 缺字段自动回退默认主题，
 * 因此玩家手工改坏 JSON 也不会让游戏崩溃。</p>
 *
 * <table>
 *   <tr><th>id</th><th>名称</th><th>气质</th></tr>
 *   <tr><td>{@code night_indigo}</td><td>暗夜靛蓝</td><td>深靛底 + 冷蓝强调，默认</td></tr>
 *   <tr><td>{@code mist_gray}</td><td>雾灰极简</td><td>中性灰 + 大留白 + 小圆角</td></tr>
 *   <tr><td>{@code neon_cyber}</td><td>霓虹赛博</td><td>墨黑底 + 青霓虹发光</td></tr>
 * </table>
 */
public final class ThemePreset {

    /** 暗夜靛蓝。 */
    public static final String NIGHT_INDIGO = "night_indigo";
    /** 雾灰极简。 */
    public static final String MIST_GRAY = "mist_gray";
    /** 霓虹赛博。 */
    public static final String NEON_CYBER = "neon_cyber";

    /** 可在设置页 / 指令中心轮换的主题名。 */
    public static final List<String> NAMES = List.of(NIGHT_INDIGO, MIST_GRAY, NEON_CYBER);

    private ThemePreset() {
    }

    /**
     * @param name 主题 id
     * @return 主题实例（未知 id 回退默认主题）
     */
    public static Theme byName(String name) {
        if (name == null || name.isBlank()) {
            return ThemeLoader.loadBuiltin(NIGHT_INDIGO);
        }
        return ThemeLoader.loadBuiltin(name);
    }

    /**
     * @param name 主题 id
     * @return 界面显示名
     */
    public static String displayName(String name) {
        String loaded = byName(name).name();
        return loaded == null || loaded.isBlank() ? name : loaded;
    }

    /** @return 配置里当前生效的主题 id */
    public static String current() {
        return ConfigManager.get().theme();
    }

    /**
     * @param current 当前主题 id
     * @return 轮换后的下一个主题 id
     */
    public static String next(String current) {
        int index = NAMES.indexOf(current);
        return NAMES.get((index + 1 + NAMES.size()) % NAMES.size());
    }

    /**
     * 切换主题并持久化（写 {@code config/canvasmenu.json}）。
     *
     * @return 生效后的主题实例
     */
    public static Theme applyNext() {
        String nextName = next(current());
        ConfigManager.setTheme(nextName);
        return ConfigManager.theme();
    }
}
