package com.canvasmenu.widget;

import com.chaossurvival.canvasmenu.gui.render.GuiCompat;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.gui.widget.AbstractWidget;
import com.chaossurvival.canvasmenu.theme.Theme;
import com.canvasmenu.command.CommandCategory;
import com.canvasmenu.render.IconBlitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * 左侧分类导航栏（Rail）。
 *
 * <p>竖向陈列的分类条目：分类贴图 + 名称 + 收藏计数；激活项有强调色竖条与微染卡面。
 * 与旧 {@code Rail}（横向滚动条）不同，这里专门服务全屏指令中心的「左导航 + 右网格」结构。</p>
 *
 * <p>「全部」「收藏」是虚拟分类，没有贴图文件时自动用 {@code glyph} 文字占位。</p>
 */
public final class CommandRail extends AbstractWidget {

    /** 单个条目高度。 */
    public static final int ITEM_HEIGHT = 30;
    /** 条目间距。 */
    private static final int ITEM_GAP = 2;
    /** 分类贴图边长。 */
    private static final int ICON_SIZE = 18;
    /** 滚动步长。 */
    private static final double SCROLL_STEP = 24.0d;

    private final List<CommandCategory> categories;
    private final IntConsumer onSelect;
    private int selected;
    private int scrollOffset;
    private int favoriteCount;

    /**
     * @param theme      当前主题
     * @param categories 分类列表（含虚拟分类）
     * @param onSelect   选中下标回调
     */
    public CommandRail(Theme theme, List<CommandCategory> categories, IntConsumer onSelect) {
        super(theme, 0, 0, 150, 320);
        this.categories = List.copyOf(categories);
        this.onSelect = onSelect;
    }

    /** @return 当前选中的分类下标 */
    public int selectedIndex() {
        return selected;
    }

    /** @return 当前选中的分类 id */
    public String selectedId() {
        return indexValid(selected) ? categories.get(selected).id() : CommandCategory.ALL;
    }

    /**
     * @param count 收藏数量（显示在「收藏」条目右侧）
     */
    public void setFavoriteCount(int count) {
        this.favoriteCount = Math.max(0, count);
    }

    /**
     * 切换分类（幂等）。
     *
     * @param index 下标
     */
    public void select(int index) {
        if (!indexValid(index) || index == selected) {
            return;
        }
        selected = index;
        if (onSelect != null) {
            onSelect.accept(index);
        }
    }

    /** 选中「全部」分类（打开面板时的默认视图）。 */
    public void selectAll() {
        for (int i = 0; i < categories.size(); i++) {
            if (CommandCategory.ALL.equals(categories.get(i).id())) {
                select(i);
                return;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        int gap = theme.gap(0);
        int rowHeight = ITEM_HEIGHT + ITEM_GAP;
        int radius = theme.radius(0);

        for (int i = 0; i < categories.size(); i++) {
            int itemY = y + i * rowHeight - scrollOffset;
            if (itemY + ITEM_HEIGHT < y || itemY > y + height) {
                continue;
            }
            CommandCategory category = categories.get(i);
            boolean active = i == selected;

            int face = active
                    ? Render2D.lerp(theme.bg().panel(), theme.accent().main(), 0.16f)
                    : theme.bg().panel();
            if (active) {
                Render2D.glowRect(graphics, x, itemY, width, ITEM_HEIGHT, radius,
                        Render2D.scaleAlpha(theme.accent().glow(), 0.45f), 1);
            }
            Render2D.roundRect(graphics, x, itemY, width, ITEM_HEIGHT, radius, face);

            // 激活竖条
            if (active) {
                Render2D.roundRect(graphics, x + 2, itemY + 4, 2, ITEM_HEIGHT - 8, 1,
                        theme.accent().main());
            }

            // 分类贴图（缺失自动占位）
            IconBlitter.drawIcon(graphics, category.icon(), category.tint(), category.glyph(),
                    x + gap + 4, itemY + (ITEM_HEIGHT - ICON_SIZE) / 2, ICON_SIZE, font, theme);

            String name = category.name();
            int textX = x + gap + 4 + ICON_SIZE + gap;
            int textY = itemY + (ITEM_HEIGHT - GuiCompat.lineHeight(font)) / 2;
            GuiCompat.text(graphics, font, name, textX, textY,
                    active ? theme.text().primary() : theme.text().secondary());

            if (CommandCategory.FAVORITES.equals(category.id()) && favoriteCount > 0) {
                String badge = String.format(Locale.ROOT, "%d", favoriteCount);
                int badgeX = x + width - gap - font.width(badge) - 2;
                GuiCompat.text(graphics, font, badge, badgeX, textY, theme.accent().main());
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        int rowHeight = ITEM_HEIGHT + ITEM_GAP;
        int relative = (int) (mouseY - y) + scrollOffset;
        int index = relative / rowHeight;
        if (indexValid(index)) {
            select(index);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        scrollOffset = clamp(scrollOffset - (int) (vertical * SCROLL_STEP), 0, maxScroll());
        return true;
    }

    private int maxScroll() {
        return Math.max(0, categories.size() * (ITEM_HEIGHT + ITEM_GAP) - height);
    }

    private boolean indexValid(int index) {
        return index >= 0 && index < categories.size();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
