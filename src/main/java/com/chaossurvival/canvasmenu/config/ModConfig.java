package com.chaossurvival.canvasmenu.config;

import com.chaossurvival.canvasmenu.theme.Palette;

/**
 * 模组配置的不可变快照。
 *
 * <p>{@code updateOwner} / {@code updateRepo} 决定唯一可信的更新源，
 * <strong>运行时不提供改源入口</strong>（防投毒）。</p>
 */
public record ModConfig(
        String theme,
        boolean takeoverInventory,
        boolean takeoverCreative,
        String updateOwner,
        String updateRepo,
        boolean checkUpdatesOnStartup,
        boolean ignorePrerelease,
        boolean highContrast,
        boolean reduceMotion,
        int tooltipTicks,
        double uiScale,
        boolean verifySha256
) {

    /** 可信更新源所属组织。 */
    public static final String TRUSTED_OWNER = "ChaosSurvivalProject";
    /** 可信更新源仓库名。 */
    public static final String TRUSTED_REPO = "CanvasMenu";

    /** tooltip 默认可读时长（tick）。 */
    public static final int DEFAULT_TOOLTIP_TICKS = 40;

    /**
     * @return 出厂默认配置
     */
    public static ModConfig defaults() {
        return new ModConfig(
                Palette.NEON_CYBER,
                true,
                false,
                TRUSTED_OWNER,
                TRUSTED_REPO,
                true,
                true,
                false,
                false,
                DEFAULT_TOOLTIP_TICKS,
                1.0d,
                true
        );
    }

    /**
     * 返回切换主题后的副本。
     *
     * @param newTheme 主题名
     * @return 新配置
     */
    public ModConfig withTheme(String newTheme) {
        return new ModConfig(newTheme, takeoverInventory, takeoverCreative, updateOwner, updateRepo,
                checkUpdatesOnStartup, ignorePrerelease, highContrast, reduceMotion,
                tooltipTicks, uiScale, verifySha256);
    }

    /**
     * 返回切换布尔开关后的副本。
     *
     * @param field 字段名
     * @param value 新值
     * @return 新配置；字段名无效时返回自身
     */
    public ModConfig withBoolean(String field, boolean value) {
        return switch (field) {
            case "takeoverInventory" -> new ModConfig(theme, value, takeoverCreative, updateOwner, updateRepo,
                    checkUpdatesOnStartup, ignorePrerelease, highContrast, reduceMotion,
                    tooltipTicks, uiScale, verifySha256);
            case "takeoverCreative" -> new ModConfig(theme, takeoverInventory, value, updateOwner, updateRepo,
                    checkUpdatesOnStartup, ignorePrerelease, highContrast, reduceMotion,
                    tooltipTicks, uiScale, verifySha256);
            case "checkUpdatesOnStartup" -> new ModConfig(theme, takeoverInventory, takeoverCreative, updateOwner, updateRepo,
                    value, ignorePrerelease, highContrast, reduceMotion, tooltipTicks, uiScale, verifySha256);
            case "ignorePrerelease" -> new ModConfig(theme, takeoverInventory, takeoverCreative, updateOwner, updateRepo,
                    checkUpdatesOnStartup, value, highContrast, reduceMotion, tooltipTicks, uiScale, verifySha256);
            case "highContrast" -> new ModConfig(theme, takeoverInventory, takeoverCreative, updateOwner, updateRepo,
                    checkUpdatesOnStartup, ignorePrerelease, value, reduceMotion, tooltipTicks, uiScale, verifySha256);
            case "reduceMotion" -> new ModConfig(theme, takeoverInventory, takeoverCreative, updateOwner, updateRepo,
                    checkUpdatesOnStartup, ignorePrerelease, highContrast, value, tooltipTicks, uiScale, verifySha256);
            case "verifySha256" -> new ModConfig(theme, takeoverInventory, takeoverCreative, updateOwner, updateRepo,
                    checkUpdatesOnStartup, ignorePrerelease, highContrast, reduceMotion, tooltipTicks, uiScale, value);
            default -> this;
        };
    }

    /**
     * 返回修改 tooltip 时长后的副本，自动夹取到合法区间。
     *
     * @param ticks 时长（tick）
     * @return 新配置
     */
    public ModConfig withTooltipTicks(int ticks) {
        int clamped = Math.max(10, Math.min(200, ticks));
        return new ModConfig(theme, takeoverInventory, takeoverCreative, updateOwner, updateRepo,
                checkUpdatesOnStartup, ignorePrerelease, highContrast, reduceMotion,
                clamped, uiScale, verifySha256);
    }

    /**
     * 返回修改 UI 缩放后的副本，自动夹取到合法区间。
     *
     * @param scale 缩放系数
     * @return 新配置
     */
    public ModConfig withUiScale(double scale) {
        double clamped = Math.max(0.75d, Math.min(1.5d, scale));
        return new ModConfig(theme, takeoverInventory, takeoverCreative, updateOwner, updateRepo,
                checkUpdatesOnStartup, ignorePrerelease, highContrast, reduceMotion,
                tooltipTicks, clamped, verifySha256);
    }
}
