package com.chaossurvival.canvasmenu.gui.screen;

import com.chaossurvival.canvasmenu.config.ConfigManager;
import com.chaossurvival.canvasmenu.config.ModConfig;
import com.chaossurvival.canvasmenu.gui.layout.Flex;
import com.chaossurvival.canvasmenu.gui.layout.Region;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.gui.widget.Button;
import com.chaossurvival.canvasmenu.gui.widget.Slider;
import com.chaossurvival.canvasmenu.gui.widget.Toggle;
import com.chaossurvival.canvasmenu.gui.widget.Widget;
import com.chaossurvival.canvasmenu.theme.Theme;
import com.canvasmenu.theme.ThemePreset;
import com.canvasmenu.update.AutoUpdater;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模组设置页：主题切换、接管开关、无障碍选项、<strong>GitHub 自动更新</strong> UI。
 *
 * <p>更新区完全由 {@link AutoUpdater} 驱动（单一链路）：状态提示、changelog 折叠、
 * 「下载并重启」。检查与下载都在虚拟线程执行，主线程只在 {@code tick()} 里读
 * {@code volatile} 状态字段改 UI；失败时 {@code AutoUpdater} 会清空文案并静默降级，
 * 本页始终保留「检查更新」重试入口，不弹错误打断游戏。</p>
 */
public final class SettingsScreen extends CanvasScreen {

    /** 布尔开关字段与翻译键。 */
    private static final String[][] TOGGLES = {
            {"takeoverInventory", "canvasmenu.settings.takeover_inventory"},
            {"takeoverCreative", "canvasmenu.settings.takeover_creative"},
            {"checkUpdatesOnStartup", "canvasmenu.settings.check_startup"},
            {"ignorePrerelease", "canvasmenu.settings.ignore_prerelease"},
            {"highContrast", "canvasmenu.settings.high_contrast"},
            {"reduceMotion", "canvasmenu.settings.reduce_motion"}
    };

    /** tooltip 时长区间。 */
    private static final int TOOLTIP_MIN = 10;
    /** tooltip 时长跨度。 */
    private static final int TOOLTIP_SPAN = 190;
    /** UI 缩放区间起点。 */
    private static final double SCALE_MIN = 0.75d;
    /** UI 缩放跨度。 */
    private static final double SCALE_SPAN = 0.75d;

    private ModConfig draft;
    private Button themeButton;
    private Button checkButton;
    private Button changelogButton;
    private Button downloadButton;
    private Button saveButton;
    private Button cancelButton;
    private final Map<String, Toggle> toggles = new LinkedHashMap<>();
    private Slider tooltipSlider;
    private Slider scaleSlider;
    private final List<Widget> leftColumn = new ArrayList<>();
    private final List<Widget> rightColumn = new ArrayList<>();

    private AutoUpdater.ReleaseInfo available;
    private String statusText = "";
    private boolean checking;
    private boolean downloading;
    private boolean changelogOpen;
    private long observedCheckId;

    /**
     * @param parent 返回目标
     * @param theme  当前主题
     */
    public SettingsScreen(Screen parent, Theme theme) {
        super(Component.translatable("canvasmenu.screen.settings"), parent, theme);
    }

    @Override
    protected void layout() {
        widgets.clear();
        draft = ConfigManager.get();
        available = AutoUpdater.available();
        leftColumn.clear();
        rightColumn.clear();
        toggles.clear();

        themeButton = new Button(theme, themeLabel(), () -> {
            draft = draft.withTheme(ThemePreset.next(draft.theme()));
            themeButton.setLabel(themeLabel());
        });
        leftColumn.add(themeButton);

        for (String[] entry : TOGGLES) {
            String field = entry[0];
            Toggle toggle = new Toggle(theme, booleanField(draft, field),
                    value -> draft = draft.withBoolean(field, value));
            toggles.put(field, toggle);
            leftColumn.add(toggle);
        }

        tooltipSlider = new Slider(theme, label("canvasmenu.settings.tooltip_ticks"),
                (draft.tooltipTicks() - TOOLTIP_MIN) / (double) TOOLTIP_SPAN,
                value -> draft = draft.withTooltipTicks(TOOLTIP_MIN + (int) (value * TOOLTIP_SPAN)));
        scaleSlider = new Slider(theme, "UI Scale",
                (draft.uiScale() - SCALE_MIN) / SCALE_SPAN,
                value -> draft = draft.withUiScale(SCALE_MIN + value * SCALE_SPAN));

        checkButton = new Button(theme, label("canvasmenu.update.check"), () -> {
            checking = true;
            statusText = label("canvasmenu.update.checking");
            AutoUpdater.checkAgain();
        });
        changelogButton = new Button(theme, label("canvasmenu.update.changelog"),
                () -> changelogOpen = !changelogOpen);
        downloadButton = new Button(theme, label("canvasmenu.update.download"), this::startDownload);
        downloadButton.setEnabled(false);
        saveButton = new Button(theme, label("canvasmenu.settings.save"), () -> {
            ConfigManager.save(draft);
            closeWithAnimation();
        });
        cancelButton = new Button(theme, label("canvasmenu.settings.cancel"), this::closeWithAnimation);

        rightColumn.add(tooltipSlider);
        rightColumn.add(scaleSlider);
        rightColumn.add(checkButton);
        rightColumn.add(changelogButton);
        rightColumn.add(downloadButton);
        rightColumn.add(saveButton);
        rightColumn.add(cancelButton);

        Region cards = region(Region.Kind.CARDS);
        int gap = theme.gap(2);
        int columnWidth = Math.max(80, (cards.width() - 3 * gap) / 2);
        Region left = Region.of(Region.Kind.CONTEXT, cards.x() + gap, cards.y() + gap,
                columnWidth, Math.max(1, cards.height() - 2 * gap));
        Region right = Region.of(Region.Kind.CONTEXT, cards.x() + 2 * gap + columnWidth,
                cards.y() + gap, columnWidth, Math.max(1, cards.height() - 2 * gap));
        new Flex(Flex.Direction.COLUMN, 1, false).layout(left, leftColumn, theme);
        new Flex(Flex.Direction.COLUMN, 1, false).layout(right, rightColumn, theme);

        widgets.addAll(leftColumn);
        widgets.addAll(rightColumn);
        refreshUpdateUi();
    }

    @Override
    protected void renderContentLayer(GuiGraphics graphics, int mouseX, int mouseY, float delta, float alpha) {
        Region hero = region(Region.Kind.HERO);
        drawTitle(graphics, title.getString(), hero.x(), hero.y() + theme.gap(1));
        super.renderContentLayer(graphics, mouseX, mouseY, delta, alpha);

        Font font = Minecraft.getInstance().font;
        Region dock = region(Region.Kind.DOCK);
        Render2D.text(graphics, font, statusText == null ? "" : statusText,
                dock.x(), dock.y() + theme.gap(0), theme.text().secondary());

        if (changelogOpen && available != null && !available.body().isBlank()) {
            int y = hero.y() + theme.gap(3);
            for (String line : available.body().split("\\n")) {
                if (y + font.lineHeight > hero.bottom()) {
                    break;
                }
                Render2D.text(graphics, font, line, hero.x(), y, theme.text().secondary());
                y += font.lineHeight + 2;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        // 检查完成：读取 AutoUpdater 的最新结果
        if (checking && !AutoUpdater.isChecking()
                && AutoUpdater.checkId() != observedCheckId) {
            observedCheckId = AutoUpdater.checkId();
            checking = false;
            available = AutoUpdater.available();
            refreshUpdateUi();
        }
        // 下载中：主线程只读进度百分比；完成后读取（可能为空的）状态文案
        if (downloading) {
            statusText = label("canvasmenu.update.downloading")
                    .replace("%d", String.valueOf(AutoUpdater.progressPercent()));
            if (!AutoUpdater.isDownloading()) {
                downloading = false;
                String result = AutoUpdater.message();
                // 失败时 AutoUpdater 返回空文案 → 静默降级，不弹错误
                statusText = result.isEmpty()
                        ? label("canvasmenu.update.up_to_date").replace("%s", AutoUpdater.localVersion())
                        : result;
                refreshUpdateUi();
            }
        }
    }

    private void refreshUpdateUi() {
        if (AutoUpdater.hasPendingSwap()) {
            statusText = "更新已下载，重启游戏后生效";
            downloadButton.setEnabled(false);
            return;
        }
        if (available != null) {
            statusText = label("canvasmenu.update.available").replace("%s", available.tag());
            downloadButton.setEnabled(!downloading);
            changelogButton.setEnabled(!available.body().isBlank());
        } else {
            statusText = label("canvasmenu.update.up_to_date")
                    .replace("%s", AutoUpdater.localVersion());
            downloadButton.setEnabled(false);
        }
    }

    /**
     * 触发一键下载。实际网络 / 磁盘工作全部由 {@link AutoUpdater} 在虚拟线程完成；
     * 本方法只改按钮状态，不做任何阻塞操作。
     */
    private void startDownload() {
        if (available == null || downloading || AutoUpdater.isDownloading()
                || AutoUpdater.hasPendingSwap()) {
            return;
        }
        downloading = true;
        downloadButton.setEnabled(false);
        statusText = label("canvasmenu.update.downloading").replace("%d", "0");
        AutoUpdater.downloadAndSchedule();
    }

    private String themeLabel() {
        return label("canvasmenu.settings.theme") + ": " + ThemePreset.displayName(draft.theme());
    }

    private static boolean booleanField(ModConfig config, String field) {
        return switch (field) {
            case "takeoverInventory" -> config.takeoverInventory();
            case "takeoverCreative" -> config.takeoverCreative();
            case "checkUpdatesOnStartup" -> config.checkUpdatesOnStartup();
            case "ignorePrerelease" -> config.ignorePrerelease();
            case "highContrast" -> config.highContrast();
            case "reduceMotion" -> config.reduceMotion();
            default -> false;
        };
    }

    private static String label(String key) {
        return Component.translatable(key).getString();
    }
}
