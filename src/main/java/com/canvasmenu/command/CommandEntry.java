package com.canvasmenu.command;

import java.util.List;
import java.util.Locale;

/**
 * 一条「贴图卡片 → Minecraft 指令」映射（对应 {@code commands.json} 的 {@code commands[]} 一项）。
 *
 * <p>这是纯数据对象，不含任何 Minecraft 类型，因此可以脱离游戏环境做单元测试。
 * 所有字段都允许缺字段回退：{@link CommandRegistry} 负责把 JSON 补齐成合法实例。</p>
 *
 * @param id          唯一标识（收藏功能以此为准，改动后旧收藏会失效）
 * @param category    分类 id，也是贴图子目录名
 * @param icon        贴图相对路径，例如 {@code weather/weather_sun.png}
 * @param label       卡片标题
 * @param description 卡片说明 / Tooltip 第二行
 * @param command     点击后填入聊天栏的完整指令（含前导 {@code /}）
 * @param keywords    搜索关键词（支持中文与英文）
 * @param tint        图标主色（ARGB），用于占位符底色
 * @param glyph       贴图不可用时的图标文字（1~2 个字符）
 */
public record CommandEntry(
        String id,
        String category,
        String icon,
        String label,
        String description,
        String command,
        List<String> keywords,
        int tint,
        String glyph
) {

    /** 紧凑构造：规范化集合与关键字符串。 */
    public CommandEntry {
        keywords = (keywords == null) ? List.of() : List.copyOf(keywords);
        id = safe(id, "command");
        category = safe(category, "misc");
        icon = safe(icon, "");
        label = safe(label, id);
        description = safe(description, "");
        command = safe(command, "");
        glyph = (glyph == null || glyph.isEmpty()) ? label.substring(0, Math.min(1, label.length())) : glyph;
    }

    /**
     * 关键词匹配（大小写不敏感，中文按原样子串匹配）。
     *
     * <p>命中范围：标题、说明、指令原文、id、分类、以及 {@code keywords} 数组。
     * 空查询视为「不过滤」。</p>
     *
     * @param query 用户输入的搜索词
     * @return 是否应当被显示
     */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (contains(id, q) || contains(label, q) || contains(description, q)
                || contains(command, q) || contains(category, q)) {
            return true;
        }
        for (String keyword : keywords) {
            if (contains(keyword, q)) {
                return true;
            }
        }
        return false;
    }

    /** @return Tooltip 首行（指令原文） */
    public String tooltipCommand() {
        return command;
    }

    /** @return Tooltip 次行（说明文字） */
    public String tooltipDescription() {
        return description.isEmpty() ? label : description;
    }

    private static boolean contains(String source, String loweredQuery) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(loweredQuery);
    }

    private static String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
