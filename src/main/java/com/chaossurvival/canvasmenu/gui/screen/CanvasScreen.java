package com.chaossurvival.canvasmenu.gui.screen;

import com.chaossurvival.canvasmenu.anim.Animator;
import com.chaossurvival.canvasmenu.anim.Tween;
import com.chaossurvival.canvasmenu.anim.transitions.Transition;
import com.chaossurvival.canvasmenu.gui.layout.Region;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.gui.widget.AbstractWidget;
import com.chaossurvival.canvasmenu.gui.widget.Widget;
import com.chaossurvival.canvasmenu.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 自研画布 Screen 基类。
 *
 * <p><strong>只自绘</strong>：背景层完全由 {@link Render2D} 绘制，
 * 不调用会触发原版 blur 的重复 {@code renderBackground}，规避 1.21.11 已知崩溃。</p>
 *
 * <p>分层渲染：背景层 → 内容层 → 交互层 → 特效层，每层独立 {@code pose()} 变换；
 * 进入/退出淡入淡出 + 0.98→1.0 轻微缩放；Tab 焦点漫游、Enter 激活、Esc 逐层返回；
 * 窗口缩放触发 {@link #layout()} 重排。</p>
 */
public abstract class CanvasScreen extends Screen {

    /** 进入动画的最小缩放。 */
    private static final float ENTER_SCALE_MIN = 0.98f;
    /** 进入动画的缩放跨度。 */
    private static final float ENTER_SCALE_SPAN = 0.02f;
    /** 焦点描边外扩像素。 */
    private static final int FOCUS_PADDING = 2;

    /** 当前主题。 */
    protected final Theme theme;
    /** 返回目标（Esc / 关闭后逐层返回）。 */
    protected final Screen parent;
    /** 所有注册组件。 */
    protected final List<Widget> widgets = new ArrayList<>();
    /** 五大区域。 */
    protected Region[] regions;

    private final Tween enterTween;
    private final Tween exitTween;
    private int focusIndex = -1;
    private boolean closing;

    /**
     * @param title  屏幕标题
     * @param parent 返回目标
     * @param theme  主题
     */
    protected CanvasScreen(Component title, Screen parent, Theme theme) {
        super(title);
        this.parent = parent;
        this.theme = theme;
        long enterMs = Transition.ENTER.millis(Animator.isReduceMotion());
        this.enterTween = Tween.of(0.0f, 1.0f, enterMs, Transition.ENTER.easing()).start();
        this.exitTween = Tween.of(1.0f, 1.0f, 0L, Transition.EXIT.easing());
        this.exitTween.set(1.0f);
    }

    @Override
    protected void init() {
        super.init();
        widgets.clear();
        regions = Region.standardLayout(width, height, theme);
        layout();
    }

    @Override
    public void resize(Minecraft client, int newWidth, int newHeight) {
        super.resize(client, newWidth, newHeight);
        regions = Region.standardLayout(width, height, theme);
        layout();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        float alpha = alpha();
        updateHover(mouseX, mouseY);

        graphics.pose().pushPose();
        float scale = ENTER_SCALE_MIN + ENTER_SCALE_SPAN * enterTween.get();
        graphics.pose().translate(width / 2.0f, height / 2.0f, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-width / 2.0f, -height / 2.0f, 0.0f);

        renderBackgroundLayer(graphics, mouseX, mouseY, delta, alpha);
        renderContentLayer(graphics, mouseX, mouseY, delta, alpha);
        renderInteractionLayer(graphics, mouseX, mouseY, delta, alpha);
        renderEffectLayer(graphics, mouseX, mouseY, delta, alpha);

        graphics.pose().popPose();

        if (closing && exitTween.isDone()) {
            finishClose();
        }
    }

    @Override
    public void tick() {
        super.tick();
        // 只有顶层 Screen 驱动全局时钟，避免嵌套 Screen 重复推进
        if (Minecraft.getInstance().screen == this) {
            Animator.tick(Animator.TICK_MS);
        }
        for (Widget widget : widgets) {
            widget.tick();
        }
    }

    // ------------------------------------------------------------------
    // 四层渲染（子类可覆写以追加内容）
    // ------------------------------------------------------------------

    /**
     * 背景层：自绘渐变 + 安全模糊，绝不调用原版 blur 路径。
     *
     * @param graphics GUI 上下文
     * @param mouseX   鼠标 x
     * @param mouseY   鼠标 y
     * @param delta    帧间隔
     * @param alpha    整体透明度
     */
    protected void renderBackgroundLayer(GuiGraphics graphics, int mouseX, int mouseY, float delta, float alpha) {
        int base = Render2D.scaleAlpha(theme.bg().base(), alpha);
        int panel = Render2D.scaleAlpha(theme.bg().panel(), alpha);
        Render2D.roundRectGradient(graphics, 0, 0, width, height, 0,
                base, base, panel, panel);
        Region cards = region(Region.Kind.CARDS);
        int pad = theme.gap(1);
        Render2D.blurRect(graphics, cards.x() - pad, cards.y() - pad,
                cards.width() + 2 * pad, cards.height() + 2 * pad,
                theme.radius(2), Render2D.scaleAlpha(theme.elevation(1), alpha * 0.35f));
    }

    /**
     * 内容层：绘制所有可见组件。
     *
     * @param graphics GUI 上下文
     * @param mouseX   鼠标 x
     * @param mouseY   鼠标 y
     * @param delta    帧间隔
     * @param alpha    整体透明度
     */
    protected void renderContentLayer(GuiGraphics graphics, int mouseX, int mouseY, float delta, float alpha) {
        for (Widget widget : widgets) {
            if (widget.isVisible()) {
                widget.render(graphics, mouseX, mouseY, delta);
            }
        }
    }

    /**
     * 交互层：键盘焦点提示。
     *
     * @param graphics GUI 上下文
     * @param mouseX   鼠标 x
     * @param mouseY   鼠标 y
     * @param delta    帧间隔
     * @param alpha    整体透明度
     */
    protected void renderInteractionLayer(GuiGraphics graphics, int mouseX, int mouseY, float delta, float alpha) {
        if (focusIndex < 0 || focusIndex >= widgets.size()) {
            return;
        }
        Widget focused = widgets.get(focusIndex);
        if (!focused.isVisible()) {
            return;
        }
        Render2D.glowRect(graphics,
                focused.x() - FOCUS_PADDING, focused.y() - FOCUS_PADDING,
                focused.width() + 2 * FOCUS_PADDING, focused.height() + 2 * FOCUS_PADDING,
                theme.radius(1), theme.accent().glow(), 2);
    }

    /**
     * 特效层：整体淡入淡出遮罩。
     *
     * @param graphics GUI 上下文
     * @param mouseX   鼠标 x
     * @param mouseY   鼠标 y
     * @param delta    帧间隔
     * @param alpha    整体透明度
     */
    protected void renderEffectLayer(GuiGraphics graphics, int mouseX, int mouseY, float delta, float alpha) {
        if (alpha >= 0.999f) {
            return;
        }
        int overlayAlpha = (int) ((1.0f - alpha) * 255.0f);
        graphics.fill(0, 0, width, height, Render2D.argb(overlayAlpha, 0, 0, 0));
    }

    // ------------------------------------------------------------------
    // 输入接管
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget widget = widgets.get(i);
            if (widget.isVisible() && widget.contains(mouseX, mouseY)
                    && widget.mouseClicked(mouseX, mouseY, button)) {
                setFocusIndex(i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget widget = widgets.get(i);
            if (widget.isVisible() && widget.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget widget = widgets.get(i);
            if (widget.isVisible() && widget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget widget = widgets.get(i);
            if (widget.isVisible() && widget.contains(mouseX, mouseY)
                    && widget.mouseScrolled(mouseX, mouseY, horizontal, vertical)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            boolean backwards = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            cycleFocus(backwards ? -1 : 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onBack();
            return true;
        }
        if (focusIndex >= 0 && focusIndex < widgets.size()
                && widgets.get(focusIndex).keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (focusIndex >= 0 && focusIndex < widgets.size()
                && widgets.get(focusIndex).charTyped(character, modifiers)) {
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Esc 由 keyPressed 统一处理，交给 onBack 做逐层返回
        return false;
    }

    // ------------------------------------------------------------------
    // 公共 API
    // ------------------------------------------------------------------

    /**
     * 注册组件。
     *
     * @param widget 组件
     */
    protected void addWidget(Widget widget) {
        widgets.add(widget);
    }

    /**
     * 取语义区域；窗口未初始化时返回零尺寸区域，避免 NPE。
     *
     * @param kind 区域类型
     * @return 区域
     */
    protected Region region(Region.Kind kind) {
        if (regions != null && regions.length > kind.ordinal() && regions[kind.ordinal()] != null) {
            return regions[kind.ordinal()];
        }
        return Region.of(kind, 0, 0, Math.max(1, width), Math.max(1, height));
    }

    /**
     * @return 当前整体透明度（进入 × 退出）
     */
    protected float alpha() {
        return enterTween.get() * exitTween.get();
    }

    /**
     * 带动画关闭并逐层返回父 Screen。
     */
    public void closeWithAnimation() {
        if (closing) {
            return;
        }
        closing = true;
        exitTween.retarget(0.0f,
                Transition.EXIT.millis(Animator.isReduceMotion()),
                Transition.EXIT.easing());
    }

    /**
     * Esc 逐层返回的钩子，子类可覆写为回到上一级子页面。
     */
    protected void onBack() {
        closeWithAnimation();
    }

    /**
     * 在指定位置绘制标题文本。
     *
     * @param graphics GUI 上下文
     * @param text     文本
     * @param x        x
     * @param y        y
     */
    protected void drawTitle(GuiGraphics graphics, String text, int x, int y) {
        Render2D.text(graphics, Minecraft.getInstance().font, text, x, y, theme.text().primary());
    }

    /**
     * 布局钩子：由 {@code init} 与 {@code resize} 调用，必须重新排布所有组件。
     */
    protected abstract void layout();

    private void finishClose() {
        for (Widget widget : widgets) {
            widget.onRemoved();
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private void updateHover(int mouseX, int mouseY) {
        for (Widget widget : widgets) {
            if (widget instanceof AbstractWidget abstractWidget) {
                abstractWidget.setHovered(widget.isVisible() && widget.contains(mouseX, mouseY));
            }
        }
    }

    private void setFocusIndex(int index) {
        if (focusIndex >= 0 && focusIndex < widgets.size()) {
            widgets.get(focusIndex).setFocused(false);
        }
        focusIndex = index;
        if (index >= 0 && index < widgets.size()) {
            widgets.get(index).setFocused(true);
        }
    }

    private void cycleFocus(int direction) {
        if (widgets.isEmpty()) {
            return;
        }
        int candidate = focusIndex;
        for (int step = 0; step < widgets.size(); step++) {
            candidate = (candidate + direction + widgets.size()) % widgets.size();
            if (widgets.get(candidate).isFocusable() && widgets.get(candidate).isVisible()) {
                setFocusIndex(candidate);
                return;
            }
        }
    }

}
