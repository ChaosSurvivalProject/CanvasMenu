package com.chaossurvival.canvasmenu.gui.screen;

import com.chaossurvival.canvasmenu.CanvasMenu;
import com.chaossurvival.canvasmenu.config.ConfigManager;
import com.chaossurvival.canvasmenu.config.ModConfig;
import com.chaossurvival.canvasmenu.gui.layout.Flex;
import com.chaossurvival.canvasmenu.gui.layout.Region;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.gui.widget.Button;
import com.chaossurvival.canvasmenu.gui.widget.Slider;
import com.chaossurvival.canvasmenu.gui.widget.Toggle;
import com.chaossurvival.canvasmenu.gui.widget.Widget;
import com.chaossurvival.canvasmenu.theme.Palette;
import com.chaossurvival.canvasmenu.theme.Theme;
import com.chaossurvival.canvasmenu.update.Downloader;
import com.chaossurvival.canvasmenu.update.UpdateChecker;
import com.chaossurvival.canvasmenu.update.Updater;
import com.chaossurvival.canvasmenu.util.Async;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * 模组设置页：主题切换、接管开关、无障碍选项、检查更新 UI。
 *
 * <p>更新区包含状态提示、changelog 折叠与「下载并重启」；
 * 下载在虚拟线程执行，进度通过 volatile 字段回传，主线程只在 {@code tick()} 里改 UI。</p>
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

    private UpdateChecker.ReleaseInfo available;
    private String statusText = "";
    private boolean checking;
    private boolean downloading;
    private boolean changelogOpen;
    private volatile int progressPercent;
    private volatile String pendingStatus;
    private long observedCheckId;

    /**
     * @param parent 返回目标
     * @param theme  主题
     */
    public SettingsScreen(Screen parent, Theme theme) {
        super(Component.translatable("canvasmenu.screen.settings"), parent, theme);
    }

    @Override
    protected void layout() {
        widgets.clear();
        draft = ConfigManager.get();
        available = CanvasMenu.latestRelease();
        leftColumn.clear();
        rightColumn.clear();
        toggles.clear();

        themeButton = new Button(theme, themeLabel(), () -> {
            draft = draft.withTheme(Palette.next(draft.theme()));
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
            CanvasMenu.checkUpdatesAsync();
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
        if (pendingStatus != null) {
            statusText = pendingStatus;
            pendingStatus = null;
        }
        if (checking && !CanvasMenu.isUpdateCheckRunning()
                && CanvasMenu.lastCheckId() != observedCheckId) {
            observedCheckId = CanvasMenu.lastCheckId();
            checking = false;
            available = CanvasMenu.latestRelease();
            refreshUpdateUi();
        }
        if (downloading) {
            statusText = label("canvasmenu.update.downloading")
                    .replace("%d", String.valueOf(progressPercent));
        }
    }

    private void refreshUpdateUi() {
        if (available != null) {
            statusText = label("canvasmenu.update.available").replace("%s", available.tag());
            downloadButton.setEnabled(true);
            changelogButton.setEnabled(!available.body().isBlank());
        } else {
            statusText = label("canvasmenu.update.up_to_date")
                    .replace("%s", CanvasMenu.localVersionString());
            downloadButton.setEnabled(false);
        }
    }

    /**
     * 下载新版本并调度重启替换。全程虚拟线程，主线程只读进度字段。
     */
    private void startDownload() {
        if (available == null || downloading) {
            return;
        }
        var jarAsset = available.jarAsset();
        if (jarAsset.isEmpty()) {
            return;
        }
        downloading = true;
        progressPercent = 0;
        downloadButton.setEnabled(false);
        UpdateChecker.Asset asset = jarAsset.get();
        String shaUrl = available.sha256Asset().map(UpdateChecker.Asset::url).orElse(null);
        boolean verify = ConfigManager.get().verifySha256();

        Async.supply(() -> {
            try {
                String expected = null;
                if (verify && shaUrl != null) {
                    expected = Downloader.fetchSha256(shaUrl);
                }
                Path destination = Path.of("mods", Updater.STAGING_DIR, asset.name());
                return Downloader.download(asset.url(), destination,
                        ratio -> progressPercent = (int) (ratio * 100.0d), expected);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((path, error) -> {
            if (error != null || path == null) {
                pendingStatus = label("canvasmenu.update.failed");
                downloading = false;
                return;
            }
            boolean scheduled = Updater.scheduleSwap(path, Path.of("mods"), null);
            pendingStatus = scheduled
                    ? label("canvasmenu.update.scheduled")
                    : label("canvasmenu.update.failed");
            downloading = false;
        });
    }

    private String themeLabel() {
        return label("canvasmenu.settings.theme") + ": " + Palette.displayName(draft.theme());
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
