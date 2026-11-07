package com.canvasmenu.command;

/**
 * 指令分类（对应 {@code commands.json} 的 {@code categories[]} 一项）。
 *
 * <p>{@code virtual} 表示这不是磁盘上的真实分类，而是运行时聚合出来的视图
 * （「全部」与「收藏」）。虚拟分类没有自己的贴图子目录。</p>
 *
 * @param id       分类标识，与 {@link CommandEntry#category()} 对应
 * @param name     界面显示名（中文）
 * @param glyph    贴图缺失时的占位字符
 * @param tint     分类主色（ARGB），用于 Rail 与卡片兜底底色
 * @param icon     分类图标相对路径（相对 {@code textures/commands/}）
 * @param virtual  是否为运行时虚拟分类
 */
public record CommandCategory(String id, String name, String glyph, int tint,
                              String icon, boolean virtual) {

    /** 「全部」视图的标识。 */
    public static final String ALL = "all";
    /** 「收藏」视图的标识。 */
    public static final String FAVORITES = "favorites";

    /**
     * 紧凑构造：保证关键字段永不为 {@code null}，避免解析坏 JSON 后到处判空。
     */
    public CommandCategory {
        id = (id == null || id.isBlank()) ? "misc" : id.trim();
        name = (name == null || name.isBlank()) ? id : name;
        glyph = (glyph == null || glyph.isEmpty()) ? "•" : glyph;
        icon = (icon == null) ? "" : icon;
    }
}
