package com.canvasmenu.widget;

import java.util.ArrayList;
import java.util.List;

/**
 * 指令卡片的<strong>流式网格布局</strong>（纯计算，无 Minecraft 依赖）。
 *
 * <p>给定区域宽高、卡片尺寸、间距与条目数，算出列数、行数、最大滚动量，
 * 以及每个可见条目相对区域左上角的坐标。视口外的条目不生成 placement，
 * Screen 只需为返回的条目复用 / 创建卡片组件，滚动时不会创建成百上千个 widget。</p>
 *
 * <p>因为不碰任何渲染 API，这个类可以直接用 JUnit / 简单断言测试。</p>
 */
public final class CommandGridLayout {

    private CommandGridLayout() {
    }

    /**
     * 一个可见条目的落位。
     *
     * @param index 条目在过滤结果中的下标
     * @param x     相对区域左上角的 x
     * @param y     相对区域左上角的 y（已减去滚动量）
     */
    public record Placement(int index, int x, int y) {
    }

    /**
     * 布局结果。
     *
     * @param columns        列数
     * @param rows           总行数
     * @param maxScroll      最大可滚动像素
     * @param firstVisibleRow 首个可见行号
     * @param visibleCount   本次可见条目数
     * @param placements     可见条目落位（不可变）
     */
    public record Result(int columns, int rows, int maxScroll, int firstVisibleRow,
                         int visibleCount, List<Placement> placements) {
    }

    /**
     * 计算布局。
     *
     * @param regionWidth  区域宽
     * @param regionHeight 区域高
     * @param cardWidth    卡片宽
     * @param cardHeight   卡片高
     * @param gap          间距（同时用于水平与垂直）
     * @param itemCount    过滤后的条目总数
     * @param scroll       当前滚动量（会被夹取到 {@code [0, maxScroll]}）
     * @return 布局结果
     */
    public static Result compute(int regionWidth, int regionHeight, int cardWidth, int cardHeight,
                                 int gap, int itemCount, int scroll) {
        int w = Math.max(1, regionWidth);
        int h = Math.max(1, regionHeight);
        int cardW = Math.max(1, cardWidth);
        int cardH = Math.max(1, cardHeight);
        int g = Math.max(0, gap);

        int columns = Math.max(1, (w + g) / (cardW + g));
        int usedWidth = columns * cardW + (columns - 1) * g;
        int startX = Math.max(0, (w - usedWidth) / 2);

        int rows = itemCount <= 0 ? 0 : (itemCount + columns - 1) / columns;
        int contentHeight = rows <= 0 ? 0 : rows * (cardH + g) - g;
        int maxScroll = Math.max(0, contentHeight - h);
        int clampedScroll = Math.max(0, Math.min(maxScroll, scroll));

        int rowStride = cardH + g;
        int firstVisibleRow = rowStride <= 0 ? 0 : clampedScroll / rowStride;

        List<Placement> placements = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            int row = i / columns;
            int column = i % columns;
            int y = row * rowStride - clampedScroll;
            // 视口裁剪：完全在上方或下方的行不参与绘制
            if (y + cardH <= 0 || y >= h) {
                continue;
            }
            placements.add(new Placement(i, startX + column * (cardW + g), y));
        }
        return new Result(columns, rows, maxScroll, firstVisibleRow,
                placements.size(), List.copyOf(placements));
    }

    /**
     * @param itemCount 条目数
     * @param columns   列数
     * @return 总行数
     */
    public static int rowCount(int itemCount, int columns) {
        if (itemCount <= 0 || columns <= 0) {
            return 0;
        }
        return (itemCount + columns - 1) / columns;
    }
}
