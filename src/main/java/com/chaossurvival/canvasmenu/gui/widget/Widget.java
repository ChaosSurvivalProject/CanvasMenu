package com.chaossurvival.canvasmenu.gui.widget;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 组件根接口：bounds / render / input。
 *
 * <p>全部输入方法都有默认空实现，简单组件只需实现 {@link #render}；
 * 复杂组件覆写所需方法即可。命中测试统一由 {@link #contains} 提供。</p>
 */
public interface Widget {

    /** @return 左上 x */
    int x();

    /** @return 左上 y */
    int y();

    /** @return 宽 */
    int width();

    /** @return 高 */
    int height();

    /**
     * 设置边界（由布局系统调用）。
     *
     * @param x x
     * @param y y
     * @param w 宽
     * @param h 高
     */
    void setBounds(int x, int y, int w, int h);

    /**
     * 自绘组件。
     *
     * @param graphics GUI 上下文
     * @param mouseX   鼠标 x
     * @param mouseY   鼠标 y
     * @param delta    帧间隔
     */
    void render(GuiGraphics graphics, int mouseX, int mouseY, float delta);

    /**
     * @param px 点 x
     * @param py 点 y
     * @return 是否命中
     */
    default boolean contains(double px, double py) {
        return px >= x() && px < x() + width() && py >= y() && py < y() + height();
    }

    /**
     * @param mouseX 鼠标 x
     * @param mouseY 鼠标 y
     * @param button 按键
     * @return 是否消费事件
     */
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * @param mouseX 鼠标 x
     * @param mouseY 鼠标 y
     * @param button 按键
     * @return 是否消费事件
     */
    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * @param mouseX 鼠标 x
     * @param mouseY 鼠标 y
     * @param button 按住的按键
     * @param dragX  拖动增量 x
     * @param dragY  拖动增量 y
     * @return 是否消费事件
     */
    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    /**
     * @param mouseX   鼠标 x
     * @param mouseY   鼠标 y
     * @param horizontal 水平滚动量
     * @param vertical   垂直滚动量
     * @return 是否消费事件
     */
    default boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return false;
    }

    /**
     * @param keyCode   键码
     * @param scanCode  扫描码
     * @param modifiers 修饰键
     * @return 是否消费事件
     */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * @param character 输入字符
     * @param modifiers 修饰键
     * @return 是否消费事件
     */
    default boolean charTyped(char character, int modifiers) {
        return false;
    }

    /** 每 tick 推进（防抖、滚动惯性等）。 */
    default void tick() {
    }

    /** @return 是否可参与键盘焦点漫游 */
    default boolean isFocusable() {
        return true;
    }

    /**
     * @param focused 是否获得焦点
     */
    default void setFocused(boolean focused) {
    }

    /** @return 是否处于焦点 */
    default boolean isFocused() {
        return false;
    }

    /** @return 是否可见 */
    default boolean isVisible() {
        return true;
    }

    /** @return 布局期望宽 */
    default int preferredWidth() {
        return width();
    }

    /** @return 布局期望高 */
    default int preferredHeight() {
        return height();
    }

    /** 从 Screen 移除时的清理钩子。 */
    default void onRemoved() {
    }
}
