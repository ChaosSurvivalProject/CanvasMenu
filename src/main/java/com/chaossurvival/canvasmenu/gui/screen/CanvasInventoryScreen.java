package com.chaossurvival.canvasmenu.gui.screen;

import com.chaossurvival.canvasmenu.gui.layout.Region;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.gui.widget.Button;
import com.chaossurvival.canvasmenu.gui.widget.ContextLayer;
import com.chaossurvival.canvasmenu.gui.widget.InventoryBridge;
import com.chaossurvival.canvasmenu.gui.widget.ItemCard;
import com.chaossurvival.canvasmenu.gui.widget.Rail;
import com.chaossurvival.canvasmenu.gui.widget.SearchBox;
import com.chaossurvival.canvasmenu.theme.Theme;
import com.canvasmenu.CommandCenter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 画布式背包：接管 {@code E} 键的视觉表现。
 *
 * <p>底层仍是原版 {@link AbstractContainerMenu} / {@link Slot}，
 * 所有点击经 {@link InventoryBridge} 发送原版网络包，生存 / 多人逻辑完全不变。</p>
 */
public final class CanvasInventoryScreen extends CanvasScreen {

    /** Rail 分类（索引 0 表示全部）。 */
    private static final List<String> CATEGORIES = List.of("全部", "装备", "材料", "方块");

    private final AbstractContainerMenu menu;
    private final List<ItemCard> allCards = new ArrayList<>();
    private final List<ItemCard> visibleCards = new ArrayList<>();

    private SearchBox search;
    private Rail rail;
    private Button closeButton;
    private Button commandButton;
    private ContextLayer context;
    private String query = "";

    /**
     * @param parent 返回目标
     * @param theme  主题
     * @param menu   原版容器菜单（通常为 {@code player.inventoryMenu}）
     */
    public CanvasInventoryScreen(Screen parent, Theme theme, AbstractContainerMenu menu) {
        super(Component.translatable("canvasmenu.screen.inventory"), parent, theme);
        this.menu = menu;
    }

    @Override
    protected void layout() {
        widgets.clear();
        regions = Region.standardLayout(width, height, theme);
        Region railRegion = region(Region.Kind.RAIL);
        Region dock = region(Region.Kind.DOCK);

        if (allCards.isEmpty() && menu != null) {
            int index = 0;
            for (Slot slot : menu.slots) {
                ItemCard card = new ItemCard(theme, slot.getItem(), slot, menu, this::openContext);
                card.setShape(shapeFor(index++));
                allCards.add(card);
            }
        }

        rail = new Rail(theme, CATEGORIES, selected -> applyFilter());
        rail.setBounds(railRegion.x(), railRegion.y(), railRegion.width(), railRegion.height());
        addWidget(rail);

        search = new SearchBox(theme, label("canvasmenu.card.search"), value -> {
            query = value;
            applyFilter();
        });
        search.setBounds(dock.x(), dock.y(),
                Math.min(200, Math.max(80, dock.width() / 3)), dock.height());
        addWidget(search);

        // 「指令中心」入口按钮：点击后打开全屏画布面板
        commandButton = new Button(theme, label("canvasmenu.card.command_center"), CommandCenter::open);
        int commandWidth = commandButton.preferredWidth();
        int searchWidth = Math.min(200, Math.max(80, dock.width() / 3));
        commandButton.setBounds(dock.x() + searchWidth + theme.gap(1), dock.y(),
                commandWidth, dock.height());
        addWidget(commandButton);

        closeButton = new Button(theme, label("canvasmenu.settings.cancel"), this::closeWithAnimation);
        int closeWidth = closeButton.preferredWidth();
        closeButton.setBounds(dock.right() - closeWidth - theme.gap(1), dock.y(),
                closeWidth, dock.height());
        addWidget(closeButton);

        applyFilter();
    }

    @Override
    protected void renderContentLayer(GuiGraphics graphics, int mouseX, int mouseY, float delta, float alpha) {
        Region hero = region(Region.Kind.HERO);
        drawTitle(graphics, title.getString(), hero.x(), hero.y() + theme.gap(1));
        Region cards = region(Region.Kind.CARDS);
        Render2D.text(graphics, Minecraft.getInstance().font,
                label("canvasmenu.card.category.all") + " · " + visibleCards.size(),
                cards.x(), cards.y() - Minecraft.getInstance().font.lineHeight - theme.gap(0),
                theme.text().secondary());
        super.renderContentLayer(graphics, mouseX, mouseY, delta, alpha);
    }

    /** 覆写画布基类的释放钩子：在组件收到释放事件后处理物品拖放换位。 */
    @Override
    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        ItemCard source = null;
        for (ItemCard card : visibleCards) {
            if (card.isDragging()) {
                source = card;
                break;
            }
        }
        boolean consumed = super.onMouseReleased(mouseX, mouseY, button);
        if (source != null) {
            for (ItemCard target : visibleCards) {
                if (target != source && target.contains(mouseX, mouseY)) {
                    InventoryBridge.swap(menu, source.slot(), target.slot());
                    break;
                }
            }
            source.endDrag();
        }
        return consumed;
    }

    /**
     * 按搜索词与分类过滤，并用 8px 基准栅格重排卡片网格。
     */
    private void applyFilter() {
        visibleCards.clear();
        String lowered = query.toLowerCase(Locale.ROOT);
        int category = rail == null ? 0 : rail.selected();
        for (ItemCard card : allCards) {
            if (!lowered.isBlank()
                    && !card.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(lowered)) {
                continue;
            }
            if (category != 0 && !matchesCategory(card, category)) {
                continue;
            }
            visibleCards.add(card);
        }

        Region cards = region(Region.Kind.CARDS);
        int gap = theme.gap(1);
        int size = ItemCard.DEFAULT_SIZE;
        int columns = Math.max(1, (cards.width() + gap) / (size + gap));
        int usedWidth = columns * size + (columns - 1) * gap;
        int startX = cards.x() + Math.max(0, (cards.width() - usedWidth) / 2);
        int startY = cards.y() + gap;
        int maxRows = Math.max(1, (cards.height() - gap + gap) / (size + gap));
        int visibleCount = Math.min(visibleCards.size(), columns * maxRows);

        widgets.removeIf(widget -> widget instanceof ItemCard);
        for (int i = 0; i < visibleCount; i++) {
            int column = i % columns;
            int row = i / columns;
            ItemCard card = visibleCards.get(i);
            card.setBounds(startX + column * (size + gap), startY + row * (size + gap), size, size);
            widgets.add(card);
        }
        if (context != null && context.isOpen()) {
            widgets.add(context);
        }
    }

    private void openContext(ItemCard card) {
        widgets.removeIf(widget -> widget instanceof ContextLayer);
        ContextLayer layer = new ContextLayer(theme, card,
                () -> widgets.removeIf(widget -> widget instanceof ContextLayer));
        Region ctx = region(Region.Kind.CONTEXT);
        int cx = Math.min(card.x() + ItemCard.DEFAULT_SIZE, Math.max(0, width - ctx.width()));
        int cy = Math.min(card.y(), Math.max(0, height - ctx.height()));
        layer.place(cx, cy);
        layer.open();
        context = layer;
        widgets.add(layer);
    }

    private static boolean matchesCategory(ItemCard card, int category) {
        // 演示性分类：按槽位序号轮换，实际项目可接入物品标签 / 创造模式分类
        int index = card.slot() == null ? 0 : card.slot().index;
        return Math.floorMod(index, 3) == category - 1;
    }

    private static ItemCard.Shape shapeFor(int index) {
        return switch (Math.floorMod(index, 3)) {
            case 0 -> ItemCard.Shape.PILL;
            case 1 -> ItemCard.Shape.CUT_CORNER;
            default -> ItemCard.Shape.HEX;
        };
    }

    private static String label(String key) {
        return Component.translatable(key).getString();
    }
}
