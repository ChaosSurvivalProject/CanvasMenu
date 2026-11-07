package com.canvasmenu.widget;

import com.chaossurvival.canvasmenu.gui.render.GuiCompat;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.theme.Theme;
import com.canvasmenu.command.CommandEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 悬停浮层 Tooltip：显示<strong>指令原文</strong>与<strong>说明文字</strong>。
 *
 * <p>由 {@code CommandPanelScreen} 在特效层绘制（不注册为 Widget，避免抢占输入），
 * 自动跟随鼠标并做边界翻转，永远不会跑出屏幕。三行内容：</p>
 * <ol>
 *   <li>指令原文（强调色，等宽感靠留白）；</li>
 *   <li>说明文字（次级文字色）；</li>
 *   <li>操作提示「左键填入聊天栏 · 右键收藏」。</li>
 * </ol>
 */
public final class CommandTooltip {

    /** 内边距。 */
    private static final int PAD = 8;
    /** 行间距。 */
    private static final int LINE_GAP = 3;
    /** 鼠标偏移。 */
    private static final int CURSOR_OFFSET = 14;
    /** 浮层最大宽度。 */
    private static final int MAX_WIDTH = 260;
    /** 操作提示（可替换为语言文件键值）。 */
    private static final String HINT = "左键填入聊天栏 · 右键收藏";

    private CommandTooltip() {
    }

    /**
     * 绘制 Tooltip。
     *
     * @param g        GUI 上下文
     * @param entry    悬停的指令条目
     * @param mouseX   鼠标 x
     * @param mouseY   鼠标 y
     * @param screenW  屏幕宽
     * @param screenH  屏幕高
     * @param theme    当前主题
     * @param font     字体
     */
    public static void render(GuiGraphics g, CommandEntry entry, int mouseX, int mouseY,
                              int screenW, int screenH, Theme theme, Font font) {
        if (g == null || entry == null || font == null) {
            return;
        }
        int lineHeight = GuiCompat.lineHeight(font);
        String line1 = entry.tooltipCommand();
        String line2 = entry.tooltipDescription();
        String line3 = HINT;

        int textWidth = Math.max(font.width(line1),
                Math.max(font.width(line2), font.width(line3)));
        int w = Math.min(MAX_WIDTH, textWidth + 2 * PAD);
        int h = 3 * lineHeight + 2 * LINE_GAP + 2 * PAD;

        int x = mouseX + CURSOR_OFFSET;
        int y = mouseY + CURSOR_OFFSET;
        if (x + w > screenW - 4) {
            x = mouseX - w - CURSOR_OFFSET;
        }
        if (y + h > screenH - 4) {
            y = mouseY - h - CURSOR_OFFSET;
        }
        x = Math.max(4, Math.min(screenW - w - 4, x));
        y = Math.max(4, Math.min(screenH - h - 4, y));

        int radius = theme.radius(1);
        Render2D.dropShadow(g, x, y, w, h, radius, theme.bg().base(), 3, 3);
        Render2D.roundRectGradient(g, x, y, w, h, radius,
                theme.bg().floating(), theme.bg().floating(),
                theme.bg().panel(), theme.bg().panel());
        Render2D.roundRect(g, x + radius, y, w - 2 * radius, 1, 0, theme.accent().main());
        Render2D.roundRect(g, x + radius, y + h - 1, w - 2 * radius, 1, 0,
                Render2D.scaleAlpha(theme.accent().glow(), 0.6f));

        int cursorY = y + PAD;
        GuiCompat.text(g, font, clip(font, line1, w - 2 * PAD), x + PAD, cursorY,
                theme.accent().main());
        cursorY += lineHeight + LINE_GAP;
        GuiCompat.text(g, font, clip(font, line2, w - 2 * PAD), x + PAD, cursorY,
                theme.text().primary());
        cursorY += lineHeight + LINE_GAP;
        GuiCompat.text(g, font, clip(font, line3, w - 2 * PAD), x + PAD, cursorY,
                theme.text().secondary());
    }

    private static String clip(Font font, String text, int maxWidth) {
        if (text == null || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (font.width(sb.toString() + text.charAt(i) + "…") > maxWidth) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb.length() == 0 ? "…" : sb + "…";
    }
}
