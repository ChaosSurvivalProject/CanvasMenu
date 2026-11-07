package com.chaossurvival.canvasmenu.gui.widget;

import com.chaossurvival.canvasmenu.anim.Tween;
import com.chaossurvival.canvasmenu.anim.transitions.Transition;
import com.chaossurvival.canvasmenu.gui.render.Easing;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/**
 * 开关：轨道 + 滑动圆点 + 颜色过渡。
 */
public final class Toggle extends AbstractWidget {

    private static final int DEFAULT_WIDTH = 44;
    private static final int DEFAULT_HEIGHT = 22;
    private static final int KNOB_INSET = 3;

    private boolean value;
    private final Consumer<Boolean> onChange;
    private final Tween slide;

    /**
     * @param theme    主题
     * @param initial  初值
     * @param onChange 变化回调
     */
    public Toggle(Theme theme, boolean initial, Consumer<Boolean> onChange) {
        super(theme, 0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        this.value = initial;
        this.onChange = onChange;
        this.slide = Tween.of(initial ? 1.0f : 0.0f, initial ? 1.0f : 0.0f,
                0L, Easing.EASE_OUT_CUBIC);
        this.slide.set(initial ? 1.0f : 0.0f);
    }

    /**
     * @return 当前值
     */
    public boolean value() {
        return value;
    }

    /**
     * 无回调地设定值（用于设置页初始化）。
     *
     * @param newValue 新值
     */
    public void setValue(boolean newValue) {
        this.value = newValue;
        this.slide.set(newValue ? 1.0f : 0.0f);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        float t = slide.get();
        int radius = height / 2;
        int track = Render2D.lerp(theme.bg().floating(), theme.accent().main(), t * 0.75f);
        Render2D.roundRect(graphics, x, y, width, height, radius, track);
        if (isFocused()) {
            Render2D.glowRect(graphics, x, y, width, height, radius, theme.accent().glow(), 2);
        }
        int knobSize = height - 2 * KNOB_INSET;
        int travel = width - knobSize - 2 * KNOB_INSET;
        int knobX = x + KNOB_INSET + (int) (travel * t);
        Render2D.roundRect(graphics, knobX, y + KNOB_INSET, knobSize, knobSize,
                knobSize / 2, theme.text().primary());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        value = !value;
        slide.retarget(value ? 1.0f : 0.0f,
                Transition.HOVER.millis(com.chaossurvival.canvasmenu.anim.Animator.isReduceMotion()),
                Transition.HOVER.easing());
        if (onChange != null) {
            onChange.accept(value);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 32) {
            return mouseClicked(centerX(), centerY(), 0);
        }
        return false;
    }
}
