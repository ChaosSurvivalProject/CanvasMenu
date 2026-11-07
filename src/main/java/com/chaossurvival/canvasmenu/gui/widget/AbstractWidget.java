package com.chaossurvival.canvasmenu.gui.widget;

import com.chaossurvival.canvasmenu.theme.Theme;

/**
 * 组件公共基类：坐标、焦点、悬停、可见性状态。
 *
 * <p>把 {@link Widget} 的样板实现集中在此，具体组件只关心自己的绘制与交互逻辑。</p>
 */
public abstract class AbstractWidget implements Widget {

    /** 当前主题，所有颜色与间距来源。 */
    protected Theme theme;

    // 供子类绘制时直接读取（布局由 setBounds 统一写入，避免每帧重复调用 getter）
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    private boolean focused;
    private boolean hovered;
    private boolean visible = true;

    /**
     * @param theme  主题
     * @param x      初始 x
     * @param y      初始 y
     * @param width  初始宽
     * @param height 初始高
     */
    protected AbstractWidget(Theme theme, int x, int y, int width, int height) {
        this.theme = theme;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void setBounds(int newX, int newY, int newWidth, int newHeight) {
        this.x = newX;
        this.y = newY;
        this.width = newWidth;
        this.height = newHeight;
    }

    @Override
    public void setFocused(boolean isFocused) {
        this.focused = isFocused;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    /** @return 是否鼠标悬停 */
    public boolean isHovered() {
        return hovered;
    }

    /**
     * @param isHovered 是否悬停
     */
    public void setHovered(boolean isHovered) {
        this.hovered = isHovered;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    /**
     * @param isVisible 是否可见
     */
    public void setVisible(boolean isVisible) {
        this.visible = isVisible;
    }

    /**
     * 更新主题（换肤时由 Screen 统一刷新）。
     *
     * @param newTheme 新主题
     */
    public void setTheme(Theme newTheme) {
        this.theme = newTheme;
    }

    /** @return 当前主题 */
    protected Theme theme() {
        return theme;
    }

    /** @return 右边界（不含） */
    protected int right() {
        return x + width;
    }

    /** @return 下边界（不含） */
    protected int bottom() {
        return y + height;
    }

    /** @return 水平中心 */
    protected int centerX() {
        return x + width / 2;
    }

    /** @return 垂直中心 */
    protected int centerY() {
        return y + height / 2;
    }
}
