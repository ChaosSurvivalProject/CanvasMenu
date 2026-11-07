package com.chaossurvival.canvasmenu.gui.widget;

import com.chaossurvival.canvasmenu.anim.Tween;
import com.chaossurvival.canvasmenu.anim.transitions.Transition;
import com.chaossurvival.canvasmenu.gui.render.Easing;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 按钮：pill / 矩形两态，hover 抬升 2px + 发光，点击回弹。
 */
public final class Button extends AbstractWidget {

    /** hover 抬升像素。 */
    private static final int LIFT_PX = 2;
    private static final int DEFAULT_WIDTH = 96;
    private static final int DEFAULT_HEIGHT = 24;
    private static final int SHADOW_LAYERS = 2;
    private static final int GLOW_LAYERS = 3;

    private String label;
    private final Runnable onPress;
    private final boolean pill;
    private final Tween hover;
    private final Tween press;
    private boolean enabled = true;

    /**
     * @param theme   主题
     * @param label   文本
     * @param onPress 点击回调
     */
    public Button(Theme theme, String label, Runnable onPress) {
        this(theme, label, onPress, true);
    }

    /**
     * @param theme   主题
     * @param label   文本
     * @param onPress 点击回调
     * @param pill    是否为 pill 形态
     */
    public Button(Theme theme, String label, Runnable onPress, boolean pill) {
        super(theme, 0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        this.label = label;
        this.onPress = onPress;
        this.pill = pill;
        this.hover = Tween.of(0.0f, 0.0f, 0L, Easing.EASE_OUT_CUBIC);
        this.press = Tween.of(0.0f, 0.0f, 0L, Easing.EASE_OUT_CUBIC);
    }

    /**
     * 动态更新按钮文本（设置页的主题轮换、状态按钮用）。
     *
     * @param newLabel 新文本
     */
    public void setLabel(String newLabel) {
        this.label = newLabel;
    }

    /**
     * @return 当前文本
     */
    public String label() {
        return label;
    }

    /**
     * @param isEnabled 是否可用
     */
    public void setEnabled(boolean isEnabled) {
        this.enabled = isEnabled;
    }

    /**
     * @return 是否可用
     */
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setHovered(boolean isHovered) {
        if (isHovered == this.isHovered()) {
            return;
        }
        super.setHovered(isHovered);
        hover.retarget(isHovered ? 1.0f : 0.0f,
                Transition.HOVER.millis(com.chaossurvival.canvasmenu.anim.Animator.isReduceMotion()),
                Transition.HOVER.easing());
    }

    @Override
    public int preferredWidth() {
        Font font = Minecraft.getInstance().font;
        return Math.max(DEFAULT_WIDTH, font.width(label) + 2 * theme.gap(1));
    }

    @Override
    public int preferredHeight() {
        return DEFAULT_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        float hoverValue = hover.get();
        float pressValue = press.get();
        int lift = (int) (LIFT_PX * hoverValue) - (int) (LIFT_PX * pressValue);
        int radius = pill ? height / 2 : theme.radius(1);
        int top = y - lift;
        int background = pill ? theme.bg().floating() : theme.bg().panel();

        Render2D.dropShadow(graphics, x, top, width, height, radius,
                theme.elevation(2), SHADOW_LAYERS, 1);
        if (hoverValue > 0.01f || isFocused()) {
            Render2D.glowRect(graphics, x, top, width, height, radius,
                    theme.accent().glow(), GLOW_LAYERS);
        }
        int border = Render2D.lerp(theme.bg().floating(), theme.accent().main(),
                Math.max(hoverValue, isFocused() ? 1.0f : 0.0f) * 0.35f);
        Render2D.strokeRoundRect(graphics, x, top, width, height, radius, 1, border, background);

        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(label);
        int textColor = enabled ? theme.text().primary() : theme.text().secondary();
        Render2D.text(graphics, font, label,
                centerX() - textWidth / 2,
                top + (height - font.lineHeight) / 2,
                textColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || !contains(mouseX, mouseY)) {
            return false;
        }
        press.set(1.0f);
        press.retarget(0.0f, Transition.HOVER.millis(
                        com.chaossurvival.canvasmenu.anim.Animator.isReduceMotion()),
                Easing.EASE_OUT_CUBIC);
        if (onPress != null) {
            onPress.run();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter / Space 由 CanvasScreen 转发为 mouseClicked 语义
        if (keyCode == 257 || keyCode == 32) {
            return mouseClicked(centerX(), centerY(), 0);
        }
        return false;
    }
}
