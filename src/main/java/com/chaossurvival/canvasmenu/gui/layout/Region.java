package com.chaossurvival.canvasmenu.gui.layout;

import com.chaossurvival.canvasmenu.theme.Theme;

/**
 * 画布区域：五大语义区域 + 不可变矩形。
 *
 * <p>所有间距由 {@link Theme#gap(int)} 推导，弃用 32×32 网格，改用「区域 + 卡片 + 留白」。</p>
 */
public final class Region {

    /** 画布五大区域。顺序即 {@link #standardLayout(int, int, Theme)} 返回数组的下标。 */
    public enum Kind {
        /** 顶部大图 / 世界信息 / 角色展示。 */
        HERO,
        /** 横向滚动分类条。 */
        RAIL,
        /** 物品卡主区。 */
        CARDS,
        /** 底部操作条。 */
        DOCK,
        /** 右键浮层 / 详情。 */
        CONTEXT
    }

    private final Kind kind;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private Region(Kind kind, int x, int y, int width, int height) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    /**
     * @param kind   区域语义
     * @param x      左上 x
     * @param y      左上 y
     * @param width  宽
     * @param height 高
     * @return 区域
     */
    public static Region of(Kind kind, int x, int y, int width, int height) {
        return new Region(kind, x, y, width, height);
    }

    /**
     * 按窗口尺寸生成标准五区布局；窗口缩放时重新调用即可完成重排。
     *
     * @param screenWidth  窗口宽
     * @param screenHeight 窗口高
     * @param theme        主题（提供全部间距）
     * @return 长度为 5 的区域数组，下标对应 {@link Kind#ordinal()}
     */
    public static Region[] standardLayout(int screenWidth, int screenHeight, Theme theme) {
        int g = theme.gap(1);
        int side = theme.gap(3);
        int heroHeight = Math.max(theme.gap(3), screenHeight / 7);
        int railHeight = theme.gap(2) + theme.gap(0);
        int dockHeight = theme.gap(3);

        int heroY = g;
        int railY = heroY + heroHeight + g;
        int dockY = Math.max(railY + railHeight + g, screenHeight - dockHeight - g);
        int cardsY = railY + railHeight + g;
        int cardsHeight = Math.max(theme.gap(2), dockY - g - cardsY);

        int contentX = side;
        int contentWidth = Math.max(theme.gap(3), screenWidth - 2 * side);

        Region[] regions = new Region[Kind.values().length];
        regions[Kind.HERO.ordinal()] = of(Kind.HERO, contentX, heroY, contentWidth, heroHeight);
        regions[Kind.RAIL.ordinal()] = of(Kind.RAIL, contentX, railY, contentWidth, railHeight);
        regions[Kind.CARDS.ordinal()] = of(Kind.CARDS, contentX, cardsY, contentWidth, cardsHeight);
        regions[Kind.DOCK.ordinal()] = of(Kind.DOCK, contentX, dockY, contentWidth, dockHeight);
        Region cards = regions[Kind.CARDS.ordinal()];
        regions[Kind.CONTEXT.ordinal()] = of(Kind.CONTEXT,
                cards.x() + theme.gap(1), cards.y() + theme.gap(1),
                Math.max(theme.gap(3), cards.width() / 3),
                Math.max(theme.gap(3), cards.height() / 2));
        return regions;
    }

    /** @return 区域语义 */
    public Kind kind() {
        return kind;
    }

    /** @return 左上 x */
    public int x() {
        return x;
    }

    /** @return 左上 y */
    public int y() {
        return y;
    }

    /** @return 宽 */
    public int width() {
        return width;
    }

    /** @return 高 */
    public int height() {
        return height;
    }

    /** @return 右边界（不含） */
    public int right() {
        return x + width;
    }

    /** @return 下边界（不含） */
    public int bottom() {
        return y + height;
    }

    /** @return 水平中心 */
    public int centerX() {
        return x + width / 2;
    }

    /** @return 垂直中心 */
    public int centerY() {
        return y + height / 2;
    }

    /**
     * 四边等量内缩。
     *
     * @param amount 内缩像素
     * @return 新区域
     */
    public Region inset(int amount) {
        return shrink(amount, amount, amount, amount);
    }

    /**
     * 四边分别内缩。
     *
     * @param left   左
     * @param top    上
     * @param right  右
     * @param bottom 下
     * @return 新区域
     */
    public Region shrink(int left, int top, int right, int bottom) {
        return new Region(kind, x + left, y + top,
                Math.max(0, width - left - right), Math.max(0, height - top - bottom));
    }

    /**
     * 平移区域。
     *
     * @param dx x 偏移
     * @param dy y 偏移
     * @return 新区域
     */
    public Region translate(int dx, int dy) {
        return new Region(kind, x + dx, y + dy, width, height);
    }

    /**
     * @param px 点 x
     * @param py 点 y
     * @return 点是否落在区域内
     */
    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    @Override
    public String toString() {
        return kind + "[" + x + "," + y + " " + width + "x" + height + "]";
    }
}
