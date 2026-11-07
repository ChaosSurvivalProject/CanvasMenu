package com.canvasmenu.screen;

import com.chaossurvival.canvasmenu.anim.Animator;
import com.chaossurvival.canvasmenu.config.ConfigManager;
import com.chaossurvival.canvasmenu.gui.layout.Region;
import com.chaossurvival.canvasmenu.gui.render.GuiCompat;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.gui.screen.CanvasScreen;
import com.chaossurvival.canvasmenu.gui.widget.Button;
import com.chaossurvival.canvasmenu.gui.widget.SearchBox;
import com.chaossurvival.canvasmenu.gui.widget.Widget;
import com.chaossurvival.canvasmenu.theme.Theme;
import com.canvasmenu.command.CommandEntry;
import com.canvasmenu.command.CommandRegistry;
import com.canvasmenu.command.Favorites;
import com.canvasmenu.render.IconTextures;
import com.canvasmenu.theme.ThemePreset;
import com.canvasmenu.widget.CommandCardWidget;
import com.canvasmenu.widget.CommandGridLayout;
import com.canvasmenu.widget.CommandRail;
import com.canvasmenu.widget.CommandTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <strong>指令中心</strong>：全屏画布面板，左侧分类 Rail + 右侧贴图卡片流式网格。
 *
 * <p>继承自研 {@link CanvasScreen}（四层渲染 / 淡入缩放 / 焦点漫游 / Esc 逐层返回），
 * <strong>完全不依赖原版格子布局</strong>：所有内容都是自绘组件与 {@code Region} 分区。</p>
 *
 * <p>交互链路：</p>
 * <pre>
 *   Rail 选分类 ─┐
 *   SearchBox ───┼─> CommandRegistry.filter() ─> CommandGridLayout ─> CommandCardWidget
 *   右键收藏 ────┘        (JSON 驱动)              (流式 + 滚动)        ├─ 左键 → ChatBridge 填聊天栏
 *                                                                      └─ 悬停 → CommandTooltip
 * </pre>
 *
 * <p><strong>线程安全</strong>：搜索框防抖与分类切换都只置 {@code filterDirty} 标记，
 * 真正的 widget 增删发生在 {@link #tick()} 中 {@code super.tick()} 遍历结束之后，
 * 避免在 {@code CanvasScreen.tick()} 的 for-each 里修改列表导致并发修改异常。</p>
 */
public final class CommandPanelScreen extends CanvasScreen {

    /** 左侧 Rail 目标宽度。 */
    private static final int RAIL_WIDTH = 152;
    /** 卡片间距取主题 gap 层级。 */
    private static final int GAP_LEVEL = 1;
    /** 滚动步长（像素 / 格）。 */
    private static final int SCROLL_STEP = 36;
    /** 状态提示显示 tick 数。 */
    private static final int STATUS_TICKS = 100;

    private CommandRail rail;
    private SearchBox search;
    private Button closeButton;
    private Button themeButton;
    private Button reloadButton;

    /** 当前过滤结果。 */
    private List<CommandEntry> visible = List.of();
    /** 最近一次网格布局结果。 */
    private CommandGridLayout.Result grid;
    /** 卡片区（相对屏幕，供裁剪与滚动命中）。 */
    private Region gridRegion;
    /** 当前搜索词。 */
    private String query = "";
    /** 当前滚动量。 */
    private int scroll;
    /** 是否需要在 tick 中重排（避免在遍历 widgets 时修改列表）。 */
    private boolean filterDirty;
    /** 底部状态提示。 */
    private String statusText = "";
    /** 状态提示剩余 tick。 */
    private int statusTicks;

    /**
     * @param parent 返回目标（通常是游戏画面或画布背包）
     * @param theme  当前主题
     */
    public CommandPanelScreen(Screen parent, Theme theme) {
        super(Component.literal("指令中心 · Command Center"), parent, theme);
    }

    // ------------------------------------------------------------------
    // 布局
    // ------------------------------------------------------------------

    @Override
    protected void layout() {
        widgets.clear();
        regions = Region.standardLayout(width, height, theme);
        Region hero = region(Region.Kind.HERO);
        Region cards = region(Region.Kind.CARDS);
        Region dock = region(Region.Kind.DOCK);
        int gap = theme.gap(GAP_LEVEL);

        // 左导航 / 右网格：从 CARDS 区域切分，避免遮挡 HERO 与 DOCK
        int railWidth = Math.min(RAIL_WIDTH, Math.max(96, cards.width() / 4));
        Region railRegion = cards.shrink(0, 0, cards.width() - railWidth, 0);
        gridRegion = cards.shrink(railWidth + gap, 0, 0, 0);

        rail = new CommandRail(theme, CommandRegistry.categories(),
                index -> {
                    scroll = 0;
                    filterDirty = true;
                });
        rail.setBounds(railRegion.x(), railRegion.y(), railRegion.width(), railRegion.height());
        rail.setFavoriteCount(Favorites.size());
        rail.selectAll();
        addWidget(rail);

        // 搜索框：右上，120ms 防抖（复用现有组件）
        int searchWidth = Math.min(220, Math.max(120, hero.width() / 3));
        search = new SearchBox(theme, "搜索指令 / 关键词…", value -> {
            query = value;
            scroll = 0;
            filterDirty = true;
        });
        search.setBounds(hero.right() - searchWidth - gap, hero.y() + gap, searchWidth, 22);
        addWidget(search);

        // 底部操作条
        closeButton = new Button(theme, "返回", this::closeWithAnimation);
        int closeWidth = closeButton.preferredWidth();
        closeButton.setBounds(dock.right() - closeWidth - gap, dock.y(), closeWidth, dock.height());
        addWidget(closeButton);

        themeButton = new Button(theme, "主题: " + ThemePreset.displayName(ThemePreset.current()),
                this::switchTheme);
        int themeWidth = themeButton.preferredWidth();
        themeButton.setBounds(closeButton.x() - themeWidth - gap, dock.y(), themeWidth, dock.height());
        addWidget(themeButton);

        reloadButton = new Button(theme, "重载配置", () -> {
            int count = CommandRegistry.reload();
            IconTextures.clear();
            IconTextures.preload(allIconPaths());
            statusText = "已重载 " + count + " 条指令";
            statusTicks = STATUS_TICKS;
            filterDirty = true;
        });
        int reloadWidth = reloadButton.preferredWidth();
        reloadButton.setBounds(themeButton.x() - reloadWidth - gap, dock.y(), reloadWidth, dock.height());
        addWidget(reloadButton);

        filterDirty = true;
    }

    // ------------------------------------------------------------------
    // 过滤与网格
    // ------------------------------------------------------------------

    /**
     * 按「分类 + 搜索词 + 收藏」重新过滤并重排卡片。
     * 只在 tick 中调用，保证不会在父级遍历 widgets 时改动列表。
     */
    private void applyFilter() {
        visible = CommandRegistry.filter(rail == null ? "all" : rail.selectedId(),
                query, Favorites.ids());

        if (gridRegion == null) {
            return;
        }
        int gap = theme.gap(GAP_LEVEL);
        grid = CommandGridLayout.compute(gridRegion.width(), gridRegion.height(),
                CommandCardWidget.DEFAULT_WIDTH, CommandCardWidget.DEFAULT_HEIGHT,
                gap, visible.size(), scroll);
        scroll = Math.max(0, Math.min(grid.maxScroll(), scroll));

        // 每次重建可见卡片：数量小（一屏 ≤ 30），换来「过滤后不会错复用」的确定性
        widgets.removeIf(widget -> widget instanceof CommandCardWidget);
        List<Widget> fresh = new ArrayList<>();
        for (CommandGridLayout.Placement placement : grid.placements()) {
            CommandEntry entry = visible.get(placement.index());
            CommandCardWidget card = new CommandCardWidget(theme, entry,
                    this::onActivated, () -> filterDirty = true);
            card.setReduceMotion(Animator.isReduceMotion());
            card.setBounds(gridRegion.x() + placement.x(),
                    gridRegion.y() + placement.y(),
                    CommandCardWidget.DEFAULT_WIDTH, CommandCardWidget.DEFAULT_HEIGHT);
            fresh.add(card);
        }
        widgets.addAll(fresh);
        if (rail != null) {
            rail.setFavoriteCount(Favorites.size());
        }
    }

    private void onActivated(CommandEntry entry, boolean chatOpened) {
        if (!chatOpened) {
            // ChatScreen 不可用时 ChatBridge 已把指令复制到剪贴板，这里给一句静默提示
            statusText = "聊天栏不可用，指令已复制：" + entry.command();
            statusTicks = STATUS_TICKS;
        }
    }

    private void switchTheme() {
        Theme next = ThemePreset.applyNext();
        Minecraft.getInstance().setScreen(new CommandPanelScreen(parent, next));
    }

    /** @return commands.json 中所有贴图路径（用于异步预加载） */
    private static List<String> allIconPaths() {
        List<String> paths = new ArrayList<>();
        for (CommandEntry entry : CommandRegistry.all()) {
            if (!entry.icon().isBlank()) {
                paths.add(entry.icon());
            }
        }
        CommandRegistry.categories().forEach(category -> {
            if (!category.icon().isBlank()) {
                paths.add(category.icon());
            }
        });
        return paths;
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    @Override
    protected void renderContentLayer(GuiGraphics graphics, int mouseX, int mouseY,
                                      float delta, float alpha) {
        Font font = Minecraft.getInstance().font;
        Region hero = region(Region.Kind.HERO);
        Region dock = region(Region.Kind.DOCK);
        int gap = theme.gap(GAP_LEVEL);

        drawTitle(graphics, title.getString(), hero.x(), hero.y() + gap);

        // 副标题：总指令数 / 当前结果数 / 当前分类
        String categoryName = rail == null ? "全部"
                : CommandRegistry.categoryById(rail.selectedId())
                        .map(category -> category.name()).orElse("全部");
        String subtitle = CommandRegistry.count() + " 条指令 · " + categoryName
                + " · 显示 " + visible.size() + " 张卡片";
        Render2D.text(graphics, font, subtitle,
                hero.x(), hero.y() + gap + GuiCompat.lineHeight(font) + gap,
                theme.text().secondary());

        // 1) 非卡片组件（Rail / 搜索框 / 按钮）不受网格裁剪影响
        for (Widget widget : widgets) {
            if (!(widget instanceof CommandCardWidget) && widget.isVisible()) {
                widget.render(graphics, mouseX, mouseY, delta);
            }
        }

        // 2) 卡片区裁剪：滚动时上下溢出的卡片不会画到 HERO / DOCK 上
        if (gridRegion != null) {
            GuiCompat.enableScissor(graphics, gridRegion.x(), gridRegion.y(),
                    gridRegion.right(), gridRegion.bottom());
            for (Widget widget : widgets) {
                if (widget instanceof CommandCardWidget && widget.isVisible()) {
                    widget.render(graphics, mouseX, mouseY, delta);
                }
            }
            GuiCompat.disableScissor(graphics);
        }

        // 3) 底部状态提示 + 操作提示
        String hint = "左键填入聊天栏（回车确认） · 右键收藏 · 滚轮翻页";
        Render2D.text(graphics, font, hint, dock.x(), dock.y() + gap, theme.text().secondary());
        if (statusTicks > 0 && !statusText.isEmpty()) {
            Render2D.text(graphics, font, statusText,
                    dock.x() + Math.min(dock.width() / 2, font.width(hint) + gap * 2),
                    dock.y() + gap, theme.accent().main());
        }
    }

    @Override
    protected void renderEffectLayer(GuiGraphics graphics, int mouseX, int mouseY,
                                     float delta, float alpha) {
        super.renderEffectLayer(graphics, mouseX, mouseY, delta, alpha);
        if (alpha < 0.6f) {
            return;
        }
        for (Widget widget : widgets) {
            if (widget instanceof CommandCardWidget card
                    && widget.isVisible() && card.isHovered()) {
                CommandTooltip.render(graphics, card.entry(), mouseX, mouseY,
                        width, height, theme, Minecraft.getInstance().font);
                break;
            }
        }
    }

    // ------------------------------------------------------------------
    // 输入
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (filterDirty) {
            filterDirty = false;
            applyFilter();
        }
        if (statusTicks > 0) {
            statusTicks--;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        // 鼠标在卡片区时消费滚动；在 Rail 上时交给父类分发给 Rail 自己处理
        if (gridRegion != null && gridRegion.contains(mouseX, mouseY)) {
            int max = grid == null ? 0 : grid.maxScroll();
            scroll = Math.max(0, Math.min(max, scroll - (int) (vertical * SCROLL_STEP)));
            filterDirty = true;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
}
