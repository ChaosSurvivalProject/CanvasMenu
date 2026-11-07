package com.chaossurvival.canvasmenu.gui.widget;

import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.DoubleConsumer;

/**
 * 滑杆：轨道 + 滑块，拖动实时插值。
 */
public final class Slider extends AbstractWidget {

    private static final int DEFAULT_WIDTH = 160;
    private static final int DEFAULT_HEIGHT = 20;
    private static final int KNOB_WIDTH = 8;
    private static final int TRACK_INSET = 2;

    private double value;
    private final DoubleConsumer onChange;
    private final String label;
    private boolean dragging;

    /**
     * @param theme    主题
     * @param label    标签
     * @param initial  初值，自动夹取到 [0,1]
     * @param onChange 变化回调
     */
    public Slider(Theme theme, String label, double initial, DoubleConsumer onChange) {
        super(theme, 0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        this.label = label;
        this.value = clamp(initial);
        this.onChange = onChange;
    }

    /**
     * @return 当前值 [0,1]
     */
    public double value() {
        return value;
    }

    /**
     * 无回调地设定值。
     *
     * @param newValue 新值
     */
    public void setValue(double newValue) {
        this.value = clamp(newValue);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int radius = theme.radius(0);
        Render2D.roundRect(graphics, x, y, width, height, radius, theme.bg().floating());
        int trackX = x + TRACK_INSET;
        int trackW = Math.max(1, width - 2 * TRACK_INSET);
        int filled = (int) (trackW * value);
        Render2D.roundRect(graphics, trackX, y + height / 2 - 1, filled, TRACK_INSET,
                1, theme.accent().main());
        int knobX = x + (int) ((width - KNOB_WIDTH) * value);
        Render2D.roundRect(graphics, knobX, y + 1, KNOB_WIDTH, height - 2,
                radius, theme.text().primary());
        if (isFocused()) {
            Render2D.glowRect(graphics, knobX, y + 1, KNOB_WIDTH, height - 2,
                    radius, theme.accent().glow(), 2);
        }
        Font font = Minecraft.getInstance().font;
        Render2D.text(graphics, font, label + " " + format(value),
                x, y - font.lineHeight - 1, theme.text().secondary());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        dragging = true;
        return updateFromMouse(mouseX);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging) {
            return false;
        }
        return updateFromMouse(mouseX);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        double step = 0.05d;
        if (keyCode == 263) {
            apply(clamp(value - step));
            return true;
        }
        if (keyCode == 262) {
            apply(clamp(value + step));
            return true;
        }
        return false;
    }

    private boolean updateFromMouse(double mouseX) {
        double ratio = (mouseX - x) / (double) Math.max(1, width);
        apply(clamp(ratio));
        return true;
    }

    private void apply(double newValue) {
        this.value = newValue;
        if (onChange != null) {
            onChange.accept(newValue);
        }
    }

    private static double clamp(double v) {
        return Math.max(0.0d, Math.min(1.0d, v));
    }

    private static String format(double v) {
        return String.format("%.2f", v);
    }
}
