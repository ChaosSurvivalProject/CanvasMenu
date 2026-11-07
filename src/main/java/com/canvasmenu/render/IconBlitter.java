package com.canvasmenu.render;

import com.chaossurvival.canvasmenu.gui.render.GuiCompat;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.theme.Theme;
import com.canvasmenu.command.CommandEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 贴图绘制器：真实 PNG 优先，<strong>占位符兜底</strong>。
 *
 * <p>两条路径：</p>
 * <ol>
 *   <li><strong>贴图可用</strong>：遍历 {@link IconTextures} 预计算的行段，
 *       用稳定的 {@code GuiGraphics.fill} 把 64×64 PNG 缩放到卡片尺寸绘制出来；</li>
 *   <li><strong>贴图不可用</strong>（缺失 / 解码失败 / 尚未加载完）：绘制
 *       「主题色圆角底 + 图标文字 {@code glyph} + 细描边 + 顶部高光」。</li>
 * </ol>
 *
 * <p>这样即使玩家把贴图全删了、或资源包没加载完，指令中心也不会出现空白方块。
 * 后续美术把真实 64×64 PNG 放到 {@code commands.json} 指定的路径，
 * 无需改任何 Java 代码即自动切换到第 1 条路径。</p>
 */
public final class IconBlitter {

    /** 占位符描边色。 */
    private static final int OUTLINE = 0xE0182028;
    /** 占位符高光色。 */
    private static final int HIGHLIGHT = 0x30FFFFFF;

    private IconBlitter() {
    }

    /**
     * 在指定位置绘制一张指令贴图。
     *
     * @param g     GUI 上下文
     * @param entry 指令条目（提供 {@code icon} / {@code tint} / {@code glyph}）
     * @param x     左上 x
     * @param y     左上 y
     * @param size  绘制边长
     * @param font  字体（占位符文字需要）
     * @param theme 当前主题
     */
    public static void draw(GuiGraphics g, CommandEntry entry, int x, int y, int size,
                            Font font, Theme theme) {
        if (g == null || entry == null || size <= 0) {
            return;
        }
        drawIcon(g, entry.icon(), entry.tint(), entry.glyph(), x, y, size, font, theme);
    }

    /**
     * 按资源路径绘制任意图标（分类 Rail 也复用此方法）。
     *
     * @param g        GUI 上下文
     * @param iconPath 相对 {@code textures/commands/} 的路径，可为空（走占位符）
     * @param tint     占位符主色
     * @param glyph    占位符图标文字
     * @param x        左上 x
     * @param y        左上 y
     * @param size     绘制边长
     * @param font     字体
     * @param theme    当前主题
     */
    public static void drawIcon(GuiGraphics g, String iconPath, int tint, String glyph,
                                int x, int y, int size, Font font, Theme theme) {
        if (g == null || size <= 0) {
            return;
        }
        IconTextures.IconData data = IconTextures.get(iconPath);
        if (data.ready()) {
            drawTexture(g, data, x, y, size);
            return;
        }
        drawFallback(g, x, y, size, tint, glyph, font, theme);
    }

    private static void drawTexture(GuiGraphics g, IconTextures.IconData data, int x, int y, int size) {
        int display = Math.max(4, Math.min(IconTextures.SIZE, size));
        for (IconTextures.Run run : data.runsFor(display)) {
            GuiCompat.fill(g,
                    x + run.x1(), y + run.y(),
                    x + run.x2(), y + run.y() + 1,
                    run.color());
        }
    }

    /**
     * 占位符：纯色微渐变圆角方牌 + 居中图标文字。风格与真实贴图徽章保持一致，
     * 因此真实贴图与占位符混排时不会突兀。
     */
    private static void drawFallback(GuiGraphics g, int x, int y, int size,
                                     int tint, String glyph, Font font, Theme theme) {
        int radius = Math.max(4, size / 5);
        int base = Render2D.withAlpha(tint, 0xFF);
        int top = Render2D.lerp(base, 0xFFFFFFFF, 0.22f);
        int bottom = Render2D.lerp(base, 0xFF000000, 0.28f);

        // 外圈细描边
        Render2D.roundRect(g, x, y, size, size, radius, OUTLINE);
        // 内部微渐变
        Render2D.roundRectGradient(g, x + 1, y + 1, size - 2, size - 2, Math.max(1, radius - 1),
                top, top, bottom, bottom);
        // 顶部高光
        Render2D.roundRect(g, x + 2, y + 2, size - 4, Math.max(2, size / 3),
                Math.max(1, radius - 2), HIGHLIGHT);

        if (font == null || glyph == null || glyph.isEmpty()) {
            return;
        }
        String shown = glyph.length() > 2 ? glyph.substring(0, 2) : glyph;
        int textWidth = font.width(shown);
        int lineHeight = GuiCompat.lineHeight(font);
        int tx = x + Math.max(0, (size - textWidth) / 2);
        int ty = y + Math.max(0, (size - lineHeight) / 2);
        // 深色描边模拟：先画偏移的暗字，再画亮字
        GuiCompat.text(g, font, shown, tx + 1, ty + 1, 0x90000000 | (theme.bg().base() & 0x00FFFFFF));
        GuiCompat.text(g, font, shown, tx, ty, 0xFFFFFFFF);
    }
}
