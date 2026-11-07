package com.chaossurvival.canvasmenu.gui.screen;

import com.chaossurvival.canvasmenu.anim.Animator;
import com.chaossurvival.canvasmenu.anim.Tween;
import com.chaossurvival.canvasmenu.anim.transitions.Transition;
import com.chaossurvival.canvasmenu.gui.layout.Region;
import com.chaossurvival.canvasmenu.gui.render.GuiCompat;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.gui.widget.AbstractWidget;
import com.chaossurvival.canvasmenu.gui.widget.Widget;
import com.chaossurvival.canvasmenu.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
// 1.21.9+ 起鼠标与键盘回调统一改为不可变事件对象（Mojang 官方映射名）
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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

    /** 1.21.9+ 的 {@code resize} 不再接收 {@link Minecraft} 参数。 */
    @Override
    public void resize(int newWidth, int newHeight) {
        super.resize(newWidth, newHeight);
        regions = Region.standardLayout(width, height, theme);
        layout();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        float alpha = alpha();
        updateHover(mouseX, mouseY);

        float scale = ENTER_SCALE_MIN + ENTER_SCALE_SPAN * enterTween.get();
        float centerX = width / 2.0f;
        float centerY = height / 2.0f;
        // 1.21.8+ 的 pose() 已从 PoseStack 换成 Matrix3x2fStack（方法名与参数个数全变）。
        // 由兼容层在运行时探测可用变换栈，探测不到就退化为「只淡入淡出、不缩放」。
        GuiCompat.withTransform(graphics, centerX, centerY, scale, () -> {
            renderBackgroundLayer(graphics, mouseX, mouseY, delta, alpha);
            renderContentLayer(graphics, mouseX, mouseY, delta, alpha);
            renderInteractionLayer(graphics, mouseX, mouseY, delta, alpha);
            renderEffectLayer(graphics, mouseX, mouseY, delta, alpha);
        });

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
    // 输入接管（1.21.9+ 事件对象适配层）
    // ------------------------------------------------------------------

    /**
     * <p>Minecraft 1.21.9 起，Mojang 把鼠标 / 键盘回调从分散的基本类型参数
     * 改成三个不可变 record：{@link MouseButtonEvent}、{@link KeyEvent}、
     * {@link CharacterEvent}。这里统一解包成 {@link Widget} 接口使用的
     * {@code (double, double, int)} 形式，使全部自绘组件与游戏版本解耦 ——
     * 组件层一行都不用改。</p>
     *
     * <p>本画布不注册任何原版 {@code GuiEventListener}，所以原版
     * {@code Screen} 的默认分发没有副作用；除键盘事件保留 super 调用
     * （它负责 Esc / 焦点导航的收尾）外，鼠标事件直接返回 {@code false}。</p>
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget widget = widgets.get(i);
            if (widget.isVisible() && widget.contains(mouseX, mouseY)
                    && widget.mouseClicked(mouseX, mouseY, button)) {
                setFocusIndex(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return onMouseReleased(event.x(), event.y(), event.button());
    }

    /**
     * 鼠标释放钩子：子类可在此接管拖拽结束逻辑（例如画布背包的物品换位）。
     * 覆写时请先调用 {@code super.onMouseReleased(...)} 让组件收到释放事件。
     *
     * @param mouseX 鼠标 x（GUI 坐标）
     * @param mouseY 鼠标 y（GUI 坐标）
     * @param button 按键编号
     * @return 是否已被组件消费
     */
    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget widget = widgets.get(i);
            if (widget.isVisible() && widget.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget widget = widgets.get(i);
            if (widget.isVisible() && widget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
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
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
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
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int codepoint = event.codepoint();
        int modifiers = event.modifiers();
        if (!Character.isValidCodePoint(codepoint)) {
            return false;
        }
        // CharacterEvent 携带 Unicode 码点，组件接口按 UTF-16 char 接收；
        // 辅助平面字符拆成代理对逐字转发，避免中文输入 / 表情把字符截坏。
        for (char character : Character.toChars(codepoint)) {
            if (focusIndex >= 0 && focusIndex < widgets.size()
                    && widgets.get(focusIndex).charTyped(character, modifiers)) {
                return true;
            }
        }
        return false;
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
