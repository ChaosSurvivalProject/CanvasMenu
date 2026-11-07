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
 * 标签栏：横向可滚动，激活项下划线滑动动画。
 */
public final class TabBar extends AbstractWidget {

    private static final int DEFAULT_WIDTH = 320;
    private static final int DEFAULT_HEIGHT = 24;
    private static final int UNDERLINE_HEIGHT = 2;
    private static final double SCROLL_STEP = 24.0d;

    private final List<String> labels;
    private final IntConsumer onSelect;
    private int selected;
    private int previous;
    private double scrollOffset;
    private final Tween underline;

    /**
     * @param theme    主题
     * @param labels   标签文本
     * @param onSelect 选中回调
     */
    public TabBar(Theme theme, List<String> labels, IntConsumer onSelect) {
        super(theme, 0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        this.labels = List.copyOf(labels);
        this.onSelect = onSelect;
        this.underline = Tween.of(1.0f, 1.0f, 0L, Transition.TAB.easing());
        this.underline.set(1.0f);
    }

    /**
     * @return 当前选中下标
     */
    public int selected() {
        return selected;
    }

    /**
     * 切换选中项（带下划线滑动动画）。
     *
     * @param index 下标
     */
    public void select(int index) {
        if (index < 0 || index >= labels.size() || index == selected) {
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
        for (int i = 0; i < labels.size(); i++) {
            int[] bounds = tabBounds(i, font);
            int color = i == selected ? theme.text().primary() : theme.text().secondary();
            Render2D.text(graphics, font, labels.get(i), bounds[0] + gap,
                    y + (height - font.lineHeight) / 2, color);
        }
        int[] from = tabBounds(previous, font);
        int[] to = tabBounds(selected, font);
        float t = underline.get();
        int underlineX = (int) (from[0] + (to[0] - from[0]) * t);
        int underlineW = (int) (from[1] + (to[1] - from[1]) * t);
        Render2D.roundRect(graphics, underlineX + gap, y + height - UNDERLINE_HEIGHT - 1,
                Math.max(1, underlineW - 2 * gap), UNDERLINE_HEIGHT, 1, theme.accent().main());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < labels.size(); i++) {
            int[] bounds = tabBounds(i, font);
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

    private int[] tabBounds(int index, Font font) {
        int gap = theme.gap(1);
        int cursor = x - (int) scrollOffset;
        for (int i = 0; i < index && i < labels.size(); i++) {
            cursor += font.width(labels.get(i)) + 2 * gap + gap;
        }
        String label = index >= 0 && index < labels.size() ? labels.get(index) : "";
        return new int[]{cursor, font.width(label) + 2 * gap};
    }

    private double maxScroll() {
        Font font = Minecraft.getInstance().font;
        int gap = theme.gap(1);
        int total = 0;
        for (String label : labels) {
            total += font.width(label) + 2 * gap + gap;
        }
        return Math.max(0.0d, total - width);
    }
}
