package com.canvasmenu.render;

import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 贴图卡片的面板绘制：九宫格圆角 + 四角微渐变 + 悬停抬升 + 发光描边。
 *
 * <p>全部颜色 / 圆角 / 间距来自 {@link Theme}，渲染代码里不写魔法色值。
 * {@code hover} 是 {@code [0,1]} 的动效进度（由 {@code Tween} 提供），
 * 因此抬升、发光、卡面微染都是<strong>连续过渡</strong>而非硬切换，
 * reduce-motion 时进度会瞬间到位。</p>
 *
 * <p>九宫格语义：{@link Render2D#nineSlice} 用 insets 区分「角隅 / 边缘」与「内容区」，
 * 内容区再叠加一层四角渐变，既保留分区层次又避免纯色平板的死板感。</p>
 */
public final class CardPaint {

    /** 悬停最大抬升像素。 */
    private static final int MAX_LIFT = 3;
    /** 收藏角标尺寸。 */
    private static final int BADGE_SIZE = 6;
    /** 收藏角标金色。 */
    private static final int FAVORITE_GOLD = 0xFFF2C14E;

    private CardPaint() {
    }

    /**
     * 绘制卡片底板（不含贴图与文字）。
     *
     * @param g        GUI 上下文
     * @param x        左上 x
     * @param y        左上 y（布局坐标，悬停抬升在本方法内部处理）
     * @param w        宽
     * @param h        高
     * @param theme    当前主题
     * @param hover    悬停进度 {@code [0,1]}
     * @param favorite 是否已收藏
     * @param tint     指令主色（用于卡面微染）
     * @return 实际绘制的顶边 y（供调用方对齐贴图与文字）
     */
    public static int drawCard(GuiGraphics g, int x, int y, int w, int h, Theme theme,
                               float hover, boolean favorite, int tint) {
        if (g == null || w <= 0 || h <= 0) {
            return y;
        }
        float progress = Math.max(0.0f, Math.min(1.0f, hover));
        int radius = theme.radius(1);
        int lift = Math.round(progress * MAX_LIFT);
        int top = y - lift;

        int edge = Render2D.lerp(theme.bg().floating(), tint, 0.10f + 0.10f * progress);
        int panel = Render2D.lerp(theme.bg().panel(), tint, 0.04f + 0.08f * progress);

        // 1) 柔和低偏移阴影：靠亮度差而非黑影表达层级
        Render2D.dropShadow(g, x, top, w, h, radius, theme.bg().base(), 3, 3 + lift);

        // 2) 悬停发光（在主体之前绘制）
        if (progress > 0.02f) {
            Render2D.glowRect(g, x, top, w, h, radius,
                    Render2D.scaleAlpha(theme.accent().glow(), 0.4f + 0.6f * progress), 2);
        }

        // 3) 九宫格圆角底板：角隅/边缘用 edge，内容区用 panel
        int inset = Math.max(2, theme.gap(0));
        Render2D.nineSlice(g, x, top, w, h, Render2D.Insets.uniform(inset), panel, edge);

        // 4) 内容区四角微渐变，制造「会呼吸」的卡面
        if (w > 4 && h > 4) {
            Render2D.roundRectGradient(g,
                    x + 2, top + 2, w - 4, h - 4, Math.max(1, radius - 2),
                    Render2D.scaleAlpha(panel, 0.55f),
                    Render2D.scaleAlpha(Render2D.lerp(panel, theme.accent().main(), 0.10f), 0.45f),
                    Render2D.scaleAlpha(theme.bg().base(), 0.35f),
                    Render2D.scaleAlpha(panel, 0.50f));
        }

        // 5) 细描边：hover 时从面板色渐变到强调色
        int stroke = Render2D.lerp(theme.bg().floating(), theme.accent().main(), progress);
        Render2D.roundRect(g, x + radius, top, w - 2 * radius, 1, 0, stroke);
        Render2D.roundRect(g, x + radius, top + h - 1, w - 2 * radius, 1, 0, stroke);
        Render2D.roundRect(g, x, top + radius, 1, h - 2 * radius, 0, stroke);
        Render2D.roundRect(g, x + w - 1, top + radius, 1, h - 2 * radius, 0, stroke);

        // 6) 收藏角标
        if (favorite) {
            int bx = x + w - BADGE_SIZE - 3;
            int by = top + 3;
            Render2D.roundRect(g, bx, by, BADGE_SIZE, BADGE_SIZE, 2, FAVORITE_GOLD);
            Render2D.roundRect(g, bx + 1, by + 1, 2, 2, 1, 0x80FFFFFF);
        }
        return top;
    }

    /**
     * 绘制一行细分隔线 / 顶部高光，用于卡片内容区与标题之间。
     *
     * @param g     GUI 上下文
     * @param x     起点 x
     * @param y     y
     * @param w     宽
     * @param color ARGB 颜色
     */
    public static void hairline(GuiGraphics g, int x, int y, int w, int color) {
        Render2D.roundRect(g, x, y, Math.max(1, w), 1, 0, color);
    }
}
