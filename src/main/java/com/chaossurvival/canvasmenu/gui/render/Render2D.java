package com.chaossurvival.canvasmenu.gui.render;

import com.chaossurvival.canvasmenu.theme.ThemeLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 全自绘 2D 图元库。
 *
 * <p>刻意只依赖 {@link GuiGraphics#fill} 与 {@code pose()}，
 * <strong>不触碰 1.21.x 中频繁变动的 {@code blit} / {@code RenderPipeline} 签名</strong>。
 * 圆角由扫描线算法生成，渐变由分段插值生成，全部支持 ARGB + 预乘 alpha 语义，
 * 颜色一律由调用方从 {@code Theme} 传入。</p>
 */
public final class Render2D {

    /** 四角渐变每行的水平分段数（越多越平滑，代价是更多 fill 调用）。 */
    private static final int GRADIENT_SEGMENTS = 6;
    /** 阴影最大层数。 */
    private static final int MAX_SHADOW_LAYERS = 6;
    /** 发光最大层数。 */
    private static final int MAX_GLOW_LAYERS = 5;

    private Render2D() {
    }

    /** 九宫格内边距。 */
    public record Insets(int top, int right, int bottom, int left) {
        /**
         * @param value 四边统一值
         * @return 统一内边距
         */
        public static Insets uniform(int value) {
            return new Insets(value, value, value, value);
        }
    }

    // ------------------------------------------------------------------
    // 颜色工具
    // ------------------------------------------------------------------

    /**
     * 组装 ARGB 颜色。
     *
     * @param a alpha
     * @param r red
     * @param g green
     * @param b blue
     * @return ARGB 整数
     */
    public static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /**
     * @param color ARGB 颜色
     * @return alpha 通道
     */
    public static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    /**
     * 替换 alpha 通道。
     *
     * @param color 原色
     * @param a     新 alpha
     * @return 新颜色
     */
    public static int withAlpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    /**
     * 按比例缩放 alpha（用于进入/退出淡入淡出）。
     *
     * @param color  原色
     * @param factor 比例，自动夹取到 [0,1]
     * @return 新颜色
     */
    public static int scaleAlpha(int color, float factor) {
        float f = Math.max(0.0f, Math.min(1.0f, factor));
        return withAlpha(color, (int) (alpha(color) * f));
    }

    /**
     * 解析 {@code #RRGGBB} / {@code #AARRGGBB}。
     *
     * @param value    色值字符串
     * @param fallback 解析失败时的回退色
     * @return ARGB 整数
     */
    public static int fromHex(String value, int fallback) {
        return ThemeLoader.parseColor(value, fallback);
    }

    /**
     * 逐通道线性插值。
     *
     * @param from 起始色
     * @param to   终色
     * @param t    进度
     * @return 插值色
     */
    public static int lerp(int from, int to, float t) {
        float f = Math.max(0.0f, Math.min(1.0f, t));
        int a = alpha(from) + (int) ((alpha(to) - alpha(from)) * f);
        int r = red(from) + (int) ((red(to) - red(from)) * f);
        int g = green(from) + (int) ((green(to) - green(from)) * f);
        int b = blue(from) + (int) ((blue(to) - blue(from)) * f);
        return argb(a, r, g, b);
    }

    /** @param c 颜色 @return red 通道 */
    public static int red(int c) {
        return (c >>> 16) & 0xFF;
    }

    /** @param c 颜色 @return green 通道 */
    public static int green(int c) {
        return (c >>> 8) & 0xFF;
    }

    /** @param c 颜色 @return blue 通道 */
    public static int blue(int c) {
        return c & 0xFF;
    }

    // ------------------------------------------------------------------
    // 图元
    // ------------------------------------------------------------------

    /**
     * 实心圆角矩形。
     *
     * @param g     GUI 上下文
     * @param x     左上 x
     * @param y     左上 y
     * @param w     宽
     * @param h     高
     * @param r     圆角半径
     * @param color ARGB 颜色
     */
    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int radius = clampRadius(r, w, h);
        if (radius <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        for (int dy = 0; dy < radius; dy++) {
            int inset = cornerInset(radius, dy);
            g.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color);
            g.fill(x + inset, y + h - 1 - dy, x + w - inset, y + h - dy, color);
        }
        g.fill(x, y + radius, x + w, y + h - radius, color);
    }

    /**
     * 四角可不同色的圆角渐变矩形（双线性插值）。
     *
     * @param g            GUI 上下文
     * @param x            左上 x
     * @param y            左上 y
     * @param w            宽
     * @param h            高
     * @param r            圆角半径
     * @param topLeft      左上色
     * @param topRight     右上色
     * @param bottomLeft   左下色
     * @param bottomRight  右下色
     */
    public static void roundRectGradient(GuiGraphics g, int x, int y, int w, int h, int r,
                                        int topLeft, int topRight, int bottomLeft, int bottomRight) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int radius = clampRadius(r, w, h);
        for (int dy = 0; dy < h; dy++) {
            float ty = h <= 1 ? 0.0f : (float) dy / (float) (h - 1);
            int left = lerp(topLeft, bottomLeft, ty);
            int right = lerp(topRight, bottomRight, ty);
            int inset = 0;
            if (dy < radius) {
                inset = cornerInset(radius, dy);
            } else if (dy >= h - radius && radius > 0) {
                inset = cornerInset(radius, h - 1 - dy);
            }
            int x1 = x + inset;
            int x2 = x + w - inset;
            if (x2 <= x1) {
                continue;
            }
            int span = x2 - x1;
            for (int s = 0; s < GRADIENT_SEGMENTS; s++) {
                int sx = x1 + span * s / GRADIENT_SEGMENTS;
                int ex = (s == GRADIENT_SEGMENTS - 1)
                        ? x2
                        : x1 + span * (s + 1) / GRADIENT_SEGMENTS;
                if (ex <= sx) {
                    continue;
                }
                float tx = (s + 0.5f) / (float) GRADIENT_SEGMENTS;
                g.fill(sx, y + dy, ex, y + dy + 1, lerp(left, right, tx));
            }
        }
    }

    /**
     * 圆角描边（通过外框 + 内缩填充实现，用于低阴影层级区分）。
     *
     * @param g         GUI 上下文
     * @param x         左上 x
     * @param y         左上 y
     * @param w         宽
     * @param h         高
     * @param r         圆角半径
     * @param thickness 描边宽度
     * @param stroke    描边色
     * @param inner     内部填充色
     */
    public static void strokeRoundRect(GuiGraphics g, int x, int y, int w, int h, int r,
                                      int thickness, int stroke, int inner) {
        roundRect(g, x, y, w, h, r, stroke);
        int t = Math.max(1, thickness);
        if (w > 2 * t && h > 2 * t) {
            roundRect(g, x + t, y + t, w - 2 * t, h - 2 * t,
                    Math.max(0, r - t), inner);
        }
    }

    /**
     * 柔和低偏移阴影：靠亮度差而非大黑影表达层级。
     *
     * @param g       GUI 上下文
     * @param x       左上 x
     * @param y       左上 y
     * @param w       宽
     * @param h       高
     * @param r       圆角半径
     * @param color   基础阴影色
     * @param layers  层数
     * @param offset  最大向下偏移
     */
    public static void dropShadow(GuiGraphics g, int x, int y, int w, int h, int r,
                                  int color, int layers, int offset) {
        int n = Math.max(1, Math.min(MAX_SHADOW_LAYERS, layers));
        for (int i = n; i >= 1; i--) {
            float strength = (float) i / (float) (n + 1);
            int shaded = scaleAlpha(color, strength * 0.5f);
            int dy = Math.max(1, offset * i / n);
            roundRect(g, x, y + dy, w, h, r, shaded);
        }
    }

    /**
     * 九宫格面板（纯色实现，避免依赖易变的纹理 blit 签名）。
     *
     * @param g      GUI 上下文
     * @param x      左上 x
     * @param y      左上 y
     * @param w      宽
     * @param h      高
     * @param insets 四边内边距
     * @param panel  面板主色
     * @param edge   边缘/角隅色
     */
    public static void nineSlice(GuiGraphics g, int x, int y, int w, int h,
                                 Insets insets, int panel, int edge) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int radius = Math.max(2, Math.min(insets.left(), insets.top()));
        roundRect(g, x, y, w, h, radius, edge);
        int ix = x + insets.left();
        int iy = y + insets.top();
        int iw = w - insets.left() - insets.right();
        int ih = h - insets.top() - insets.bottom();
        if (iw > 0 && ih > 0) {
            g.fill(ix, iy, ix + iw, iy + ih, panel);
        }
    }

    /**
     * 安全模糊矩形：失败自动降级为半透明纯色。
     *
     * @param g        GUI 上下文
     * @param x        左上 x
     * @param y        左上 y
     * @param w        宽
     * @param h        高
     * @param r        圆角半径
     * @param fallback 降级色
     */
    public static void blurRect(GuiGraphics g, int x, int y, int w, int h, int r, int fallback) {
        BlurLayer.safeBlur(g, x, y, w, h, r, fallback);
    }

    /**
     * 强调色外发光（hover / focus）。应在组件主体<strong>之前</strong>调用。
     *
     * @param g      GUI 上下文
     * @param x      左上 x
     * @param y      左上 y
     * @param w      宽
     * @param h      高
     * @param r      圆角半径
     * @param glow   发光色
     * @param layers 层数
     */
    public static void glowRect(GuiGraphics g, int x, int y, int w, int h, int r,
                                int glow, int layers) {
        int n = Math.max(1, Math.min(MAX_GLOW_LAYERS, layers));
        for (int i = n; i >= 1; i--) {
            float strength = (float) (n - i + 1) / (float) n;
            int color = scaleAlpha(glow, strength * 0.35f);
            roundRect(g, x - i, y - i, w + 2 * i, h + 2 * i, r + i, color);
        }
    }

    /**
     * 绘制文本（颜色由 Theme 提供）。
     *
     * @param g     GUI 上下文
     * @param font  字体
     * @param text  文本
     * @param x     x
     * @param y     y
     * @param color ARGB 颜色
     */
    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        g.drawString(font, text, x, y, color);
    }

    // ------------------------------------------------------------------
    // 内部算法
    // ------------------------------------------------------------------

    private static int clampRadius(int r, int w, int h) {
        return Math.max(0, Math.min(r, Math.min(w, h) / 2));
    }

    /**
     * 圆角扫描线：返回第 {@code dy} 行左右各需缩进的像素数。
     * 圆心位于 {@code (r, r)}，按像素中心求圆方程的半宽。
     */
    private static int cornerInset(int r, int dy) {
        if (r <= 0) {
            return 0;
        }
        double vertical = r - dy - 0.5d;
        double halfWidth = Math.sqrt(Math.max(0.0d, (double) r * r - vertical * vertical));
        return (int) Math.ceil(Math.max(0.0d, r - halfWidth));
    }
}
