package com.chaossurvival.canvasmenu.gui.layout;

import com.chaossurvival.canvasmenu.gui.widget.Widget;
import com.chaossurvival.canvasmenu.theme.Theme;

import java.util.List;

/**
 * 流式布局：横向 / 纵向依次排列，可选换行。
 *
 * <p>间距层级来自 {@code Theme.gap}；子组件尺寸取自 {@link Widget#preferredWidth()} /
 * {@link Widget#preferredHeight()}。</p>
 */
public final class Flex implements Layout {

    /** 排列方向。 */
    public enum Direction {
        /** 横向。 */
        ROW,
        /** 纵向。 */
        COLUMN
    }

    private final Direction direction;
    private final int gapLevel;
    private final boolean wrap;

    /**
     * @param direction 排列方向
     * @param gapLevel  间距层级（索引进 Theme.gap）
     * @param wrap      横向排列时是否自动换行
     */
    public Flex(Direction direction, int gapLevel, boolean wrap) {
        this.direction = direction;
        this.gapLevel = gapLevel;
        this.wrap = wrap;
    }

    /**
     * 构造横向单行布局。
     *
     * @return Flex 实例
     */
    public static Flex row() {
        return new Flex(Direction.ROW, 1, false);
    }

    /**
     * 构造横向自动换行布局（卡片网格）。
     *
     * @return Flex 实例
     */
    public static Flex wrapRow() {
        return new Flex(Direction.ROW, 1, true);
    }

    /**
     * 构造纵向布局。
     *
     * @return Flex 实例
     */
    public static Flex column() {
        return new Flex(Direction.COLUMN, 1, false);
    }

    @Override
    public void layout(Region parent, List<? extends Widget> children, Theme theme) {
        int gap = theme.gap(gapLevel);
        int cursorX = parent.x() + gap;
        int cursorY = parent.y() + gap;
        int rowHeight = 0;

        for (Widget child : children) {
            int pw = Math.max(1, child.preferredWidth());
            int ph = Math.max(1, child.preferredHeight());

            if (direction == Direction.ROW && wrap
                    && cursorX + pw > parent.right() - gap
                    && cursorX > parent.x() + gap) {
                cursorX = parent.x() + gap;
                cursorY += rowHeight + gap;
                rowHeight = 0;
            }

            child.setBounds(cursorX, cursorY, pw, ph);

            if (direction == Direction.ROW) {
                cursorX += pw + gap;
                rowHeight = Math.max(rowHeight, ph);
            } else {
                cursorY += ph + gap;
            }
        }
    }
}
