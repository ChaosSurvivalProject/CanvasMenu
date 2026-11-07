package com.chaossurvival.canvasmenu.gui.widget;

import com.chaossurvival.canvasmenu.anim.Tween;
import com.chaossurvival.canvasmenu.anim.transitions.Transition;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * 横向滚动分类条（Rail 区域）。
 *
 * <p>与 {@link TabBar} 的区别：Rail 是「可滚动的分类陈列」，条目带淡色卡面，
 * 激活项有滑动下划线；滚动量单独维护，供卡片区按分类过滤。</p>
 */
public final class Rail extends AbstractWidget {

    private static final int DEFAULT_WIDTH = 480;
    private static final int DEFAULT_HEIGHT = 28;
    private static final int ITEM_RADIUS_LEVEL = 0;
    private static final int UNDERLINE_HEIGHT = 2;
    private static final double SCROLL_STEP = 32.0d;

    private final List<String> categories;
    private final IntConsumer onSelect;
    private int selected;
    private int previous;
    private double scrollOffset;
    private final Tween underline;

    /**
     * @param theme      主题
     * @param categories 分类名
     * @param onSelect   选中回调
     */
    public Rail(Theme theme, List<String> categories, IntConsumer onSelect) {
        super(theme, 0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        this.categories = List.copyOf(categories);
        this.onSelect = onSelect;
        this.underline = Tween.of(1.0f, 1.0f, 0L, Transition.TAB.easing());
        this.underline.set(1.0f);
    }

    /**
     * @return 当前分类下标
     */
    public int selected() {
        return selected;
    }

    /**
     * @param index 下标
     * @return 分类名
     */
    public String category(int index) {
        return index >= 0 && index < categories.size() ? categories.get(index) : "";
    }

    /**
     * 切换分类。
     *
     * @param index 下标
     */
    public void select(int index) {
        if (index < 0 || index >= categories.size() || index == selected) {
            return;
        }
        previous = selected;
        selected = index;
        underline.set(0.0f);
        underline.retarget(1.0f,
                Transition.TAB.millis(com.chaossurvival.canvasmenu.anim.Animator.isReduceMotion()),
                Transition.TAB.easing());
        if (onSelect != null) {
            onSelect.accept(index);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        int gap = theme.gap(1);
        for (int i = 0; i < categories.size(); i++) {
            int[] bounds = itemBounds(i, font);
            int face = i == selected
                    ? Render2D.lerp(theme.bg().floating(), theme.accent().main(), 0.12f)
                    : theme.bg().panel();
            Render2D.roundRect(graphics, bounds[0], y, bounds[1], height,
                    theme.radius(ITEM_RADIUS_LEVEL), face);
            Render2D.text(graphics, font, categories.get(i), bounds[0] + gap,
                    y + (height - font.lineHeight) / 2,
                    i == selected ? theme.text().primary() : theme.text().secondary());
        }
        int[] from = itemBounds(previous, font);
        int[] to = itemBounds(selected, font);
        float t = underline.get();
        int ux = (int) (from[0] + (to[0] - from[0]) * t);
        int uw = (int) (from[1] + (to[1] - from[1]) * t);
        Render2D.roundRect(graphics, ux, y + height - UNDERLINE_HEIGHT,
                Math.max(1, uw), UNDERLINE_HEIGHT, 1, theme.accent().main());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < categories.size(); i++) {
            int[] bounds = itemBounds(i, font);
            if (mouseX >= bounds[0] && mouseX < bounds[0] + bounds[1]) {
                select(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        scrollOffset = Math.max(0.0d, Math.min(maxScroll(), scrollOffset - vertical * SCROLL_STEP));
        return true;
    }

    private int[] itemBounds(int index, Font font) {
        int gap = theme.gap(1);
        int cursor = x - (int) scrollOffset;
        for (int i = 0; i < index && i < categories.size(); i++) {
            cursor += font.width(categories.get(i)) + 2 * gap + gap;
        }
        String label = index >= 0 && index < categories.size() ? categories.get(index) : "";
        return new int[]{cursor, font.width(label) + 2 * gap};
    }

    private double maxScroll() {
        Font font = Minecraft.getInstance().font;
        int gap = theme.gap(1);
        int total = 0;
        for (String label : categories) {
            total += font.width(label) + 2 * gap + gap;
        }
        return Math.max(0.0d, total - width);
    }
}
