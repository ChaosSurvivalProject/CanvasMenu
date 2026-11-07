package com.chaossurvival.canvasmenu.theme;

/**
 * 不可变主题 token 集合。
 *
 * <p>所有颜色为 ARGB {@code int}；圆角、间距、层级高度、动效时长一律从这里取，
 * <strong>禁止在渲染代码里写魔法数字</strong>。使用 record 保证不可变与线程安全。</p>
 */
public record Theme(
        String name,
        int[] radius,
        int[] gap,
        int[] elevation,
        Bg bg,
        Text text,
        Accent accent,
        Fonts font,
        Motion motion
) {

    /** 背景三级色。 */
    public record Bg(int base, int panel, int floating) {
    }

    /** 文字两级色。 */
    public record Text(int primary, int secondary) {
    }

    /** 单一强调色及其发光色。 */
    public record Accent(int main, int glow) {
    }

    /** 字体族描述（预留中英文与字号缩放）。 */
    public record Fonts(String display, String body) {
    }

    /** 动效三档时长（毫秒）与统一曲线。 */
    public record Motion(int fast, int normal, int slow, String curve) {
    }

    /** 默认主题名。 */
    public static final String DEFAULT_NAME = "Default";

    /** 默认强调色（青霓虹）。 */
    public static final int DEFAULT_ACCENT = 0xFF4FD1C5;

    /**
     * 构造内置默认主题，数值与 {@code assets/canvasmenu/themes/default.json} 一致。
     *
     * @return 默认主题
     */
    public static Theme defaults() {
        return new Theme(
                DEFAULT_NAME,
                new int[]{6, 10, 16, 24},
                new int[]{8, 12, 16, 24},
                new int[]{0x00000000, 0x22FFFFFF, 0x33FFFFFF, 0x44FFFFFF},
                new Bg(0xFF0E1116, 0xFF161B22, 0xFF1C232D),
                new Text(0xFFE6EDF3, 0xFF9BA7B4),
                new Accent(DEFAULT_ACCENT, 0xAA4FD1C5),
                new Fonts("serif-ish", "mono-ish"),
                new Motion(150, 240, 320, "cubic-bezier(0.22,1,0.36,1)")
        );
    }

    /**
     * 取指定层级的圆角半径。
     *
     * @param level 层级，越界自动收敛到合法范围
     * @return 半径（像素）
     */
    public int radius(int level) {
        return radius[clampLevel(level, radius.length)];
    }

    /**
     * 取指定层级的间距（8px 基准栅格的倍数）。
     *
     * @param level 层级，越界自动收敛到合法范围
     * @return 间距（像素）
     */
    public int gap(int level) {
        return gap[clampLevel(level, gap.length)];
    }

    /**
     * 取指定层级的高度色（靠亮度差表达层级，不用厚重投影）。
     *
     * @param level 层级，越界自动收敛到合法范围
     * @return ARGB 颜色
     */
    public int elevation(int level) {
        return elevation[clampLevel(level, elevation.length)];
    }

    private static int clampLevel(int level, int length) {
        if (length == 0) {
            return 0;
        }
        return Math.max(0, Math.min(length - 1, level));
    }

    /**
     * 返回改名副本。
     *
     * @param newName 新主题名
     * @return 新主题实例
     */
    public Theme withName(String newName) {
        return new Theme(newName, radius, gap, elevation, bg, text, accent, font, motion);
    }

    /**
     * 高对比模式：把次级文字提亮，并强化面板与背景的亮度差。
     *
     * @param enabled 是否启用高对比
     * @return 适配后的主题
     */
    public Theme forHighContrast(boolean enabled) {
        if (!enabled) {
            return this;
        }
        Text highText = new Text(0xFFFFFFFF, 0xFFD6DEE8);
        Bg highBg = new Bg(0xFF000000, bg.panel(), bg.floating());
        return new Theme(name, radius, gap, elevation, highBg, highText, accent, font, motion);
    }
}
