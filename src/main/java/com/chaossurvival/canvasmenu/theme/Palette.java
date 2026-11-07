package com.chaossurvival.canvasmenu.theme;

import java.util.List;

/**
 * 三套预设调色板（暗靛 / 墨黑 / 雾灰 + 单一强调色）。
 *
 * <p>预设以代码常量形式提供，保证即使资源文件缺失也能启动；
 * 外部 JSON 主题由 {@link ThemeLoader} 加载，二者共用同一 {@link Theme} 结构。</p>
 */
public final class Palette {

    /** 青霓虹。 */
    public static final String NEON_CYBER = "neon_cyber";
    /** 金箔暗蓝。 */
    public static final String GENSIN_ARCHIVE = "genshin_archive";
    /** 死亡搁浅式极简。 */
    public static final String MONO_MINIMAL = "mono_minimal";
    /** 默认（与 default.json 一致）。 */
    public static final String DEFAULT = "default";

    /** 可在设置页轮换的主题名。 */
    public static final List<String> NAMES = List.of(NEON_CYBER, GENSIN_ARCHIVE, MONO_MINIMAL);

    private Palette() {
    }

    /** @return Neon Cyber 主题 */
    public static Theme neonCyber() {
        return of("Neon Cyber",
                new int[]{6, 10, 16, 24}, new int[]{8, 12, 16, 24},
                new int[]{0x00000000, 0x22FFFFFF, 0x33FFFFFF, 0x44FFFFFF},
                0xFF0E1116, 0xFF161B22, 0xFF1C232D,
                0xFFE6EDF3, 0xFF9BA7B4,
                0xFF4FD1C5, 0xAA4FD1C5);
    }

    /** @return Genshin Archive 主题 */
    public static Theme genshinArchive() {
        return of("Genshin Archive",
                new int[]{4, 8, 14, 20}, new int[]{8, 14, 20, 28},
                new int[]{0x00000000, 0x1ADBC8A0, 0x2ADBC8A0, 0x3ADBC8A0},
                0xFF0C1220, 0xFF141C2E, 0xFF1B2438,
                0xFFF2EAD8, 0xFFA9B4C8,
                0xFFD3B87A, 0x99D3B87A);
    }

    /** @return Mono Minimal 主题 */
    public static Theme monoMinimal() {
        return of("Mono Minimal",
                new int[]{2, 4, 6, 10}, new int[]{12, 18, 28, 40},
                new int[]{0x00000000, 0x12FFFFFF, 0x1EFFFFFF, 0x2AFFFFFF},
                0xFF08090B, 0xFF101215, 0xFF171A1E,
                0xFFF4F4F4, 0xFF82868C,
                0xFFE8E8E8, 0x66FFFFFF);
    }

    /**
     * 按名称取预设，未知名称回退默认主题。
     *
     * @param name 主题名
     * @return 主题实例
     */
    public static Theme byName(String name) {
        if (name == null) {
            return Theme.defaults();
        }
        return switch (name) {
            case NEON_CYBER -> neonCyber();
            case GENSIN_ARCHIVE -> genshinArchive();
            case MONO_MINIMAL -> monoMinimal();
            default -> Theme.defaults();
        };
    }

    /**
     * @param name 主题名
     * @return 用于界面展示的名称
     */
    public static String displayName(String name) {
        return byName(name).name();
    }

    /**
     * 返回列表中的下一个主题名，用于设置页轮换。
     *
     * @param current 当前主题名
     * @return 下一个主题名
     */
    public static String next(String current) {
        int index = NAMES.indexOf(current);
        return NAMES.get((index + 1 + NAMES.size()) % NAMES.size());
    }

    private static Theme of(String name, int[] radius, int[] gap, int[] elevation,
                            int base, int panel, int floating, int primary, int secondary,
                            int accent, int glow) {
        Theme d = Theme.defaults();
        return new Theme(name, radius, gap, elevation,
                new Theme.Bg(base, panel, floating),
                new Theme.Text(primary, secondary),
                new Theme.Accent(accent, glow),
                d.font(), d.motion());
    }
}
