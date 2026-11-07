package com.canvasmenu.widget;

import com.chaossurvival.canvasmenu.gui.render.GuiCompat;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.gui.widget.AbstractWidget;
import com.chaossurvival.canvasmenu.theme.Theme;
import com.canvasmenu.command.ChatBridge;
import com.canvasmenu.command.CommandEntry;
import com.canvasmenu.command.Favorites;
import com.canvasmenu.render.CardPaint;
import com.canvasmenu.render.IconBlitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiConsumer;

/**
 * 贴图指令卡片 —— 指令中心的核心交互组件。
 *
 * <p><strong>视觉</strong>：九宫格圆角面板、四角微渐变、细描边；
 * 悬停时连续抬升（最多 3px）+ 强调色发光；已收藏时右上角金色角标；
 * 贴图由 {@link IconBlitter} 绘制，缺图自动降级为「主色方牌 + 图标文字」。</p>
 *
 * <p><strong>交互</strong>：</p>
 * <ul>
 *   <li><strong>左键</strong>：打开聊天栏并填入指令，<strong>不自动发送</strong>，玩家确认后回车；</li>
 *   <li><strong>右键</strong>：加入 / 移出「收藏」分类（持久化到 config）；</li>
 *   <li><strong>Enter / Space</strong>：键盘等价于左键（无障碍）；</li>
 *   <li><strong>悬停</strong>：由 Screen 读取 {@link #isHovered()} 绘制 Tooltip。</li>
 * </ul>
 *
 * <p>悬停动效进度由本组件在 {@link #tick()} 中自行逼近，不额外起线程；
 * reduce-motion 时一步到位。所有颜色取自 {@link Theme}。</p>
 */
public final class CommandCardWidget extends AbstractWidget {

    /** 默认卡片尺寸。 */
    public static final int DEFAULT_WIDTH = 96;
    /** 默认卡片高度。 */
    public static final int DEFAULT_HEIGHT = 104;
    /** 每 tick 悬停进度逼近系数。 */
    private static final float HOVER_STEP = 0.28f;
    /** 键盘激活键。 */
    private static final int KEY_SPACE = 32;

    private final CommandEntry entry;
    private final BiConsumer<CommandEntry, Boolean> onActivated;
    private final Runnable onFavoriteChanged;
    private boolean favorite;
    private float hoverProgress;
    private boolean reduceMotion;

    /**
     * @param theme              当前主题
     * @param entry              指令条目
     * @param onActivated        左键 / 回车激活回调，第二个参数表示聊天栏是否成功打开
     * @param onFavoriteChanged  收藏状态变化回调（Screen 需要重排「收藏」分类）
     */
    public CommandCardWidget(Theme theme, CommandEntry entry,
                             BiConsumer<CommandEntry, Boolean> onActivated,
                             Runnable onFavoriteChanged) {
        super(theme, 0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        this.entry = entry;
        this.onActivated = onActivated;
        this.onFavoriteChanged = onFavoriteChanged;
        this.favorite = Favorites.contains(entry.id());
    }

    /** @return 绑定的指令条目（Tooltip 与收藏都读它） */
    public CommandEntry entry() {
        return entry;
    }

    /** @return 当前是否已收藏 */
    public boolean isFavorite() {
        return favorite;
    }

    /** @return 悬停动效进度 {@code [0,1]} */
    public float hoverProgress() {
        return hoverProgress;
    }

    /**
     * @param value 是否启用 reduce-motion（减少动态效果）
     */
    public void setReduceMotion(boolean value) {
        this.reduceMotion = value;
    }

    @Override
    public void tick() {
        float target = (isHovered() || isFocused()) ? 1.0f : 0.0f;
        if (reduceMotion) {
            hoverProgress = target;
        } else {
            hoverProgress += (target - hoverProgress) * HOVER_STEP;
            if (Math.abs(target - hoverProgress) < 0.005f) {
                hoverProgress = target;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        int gap = theme.gap(0);
        int top = CardPaint.drawCard(graphics, x, y, width, height, theme,
                hoverProgress, favorite, entry.tint());

        // 图标区：占据标题以上的空间，居中；尺寸随卡片自适应
        int lineHeight = GuiCompat.lineHeight(font);
        int reserved = lineHeight + gap * 2;
        int iconSize = Math.max(16, Math.min(width - 2 * gap, height - gap - reserved));
        int iconX = x + Math.max(0, (width - iconSize) / 2);
        int iconY = top + gap + Math.max(0, (height - gap - reserved - iconSize) / 2);
        IconBlitter.draw(graphics, entry, iconX, iconY, iconSize, font, theme);

        // 细分隔线
        CardPaint.hairline(graphics, x + gap, top + height - reserved - gap / 2,
                width - 2 * gap, Render2D.scaleAlpha(theme.text().secondary(), 0.28f));

        // 标题（超宽自动截断，避免溢出卡片）
        String label = ellipsize(font, entry.label(), width - 2 * gap);
        int textY = top + height - gap - lineHeight;
        GuiCompat.text(graphics, font, label,
                x + Math.max(0, (width - font.width(label)) / 2), textY,
                hoverProgress > 0.5f ? theme.text().primary() : theme.text().secondary());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (button == 0) {
            activate();
            return true;
        }
        if (button == 1) {
            favorite = Favorites.toggle(entry.id());
            if (onFavoriteChanged != null) {
                onFavoriteChanged.run();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == KEY_SPACE) {
            activate();
            return true;
        }
        return false;
    }

    private void activate() {
        boolean opened = ChatBridge.fill(entry.command());
        if (onActivated != null) {
            onActivated.accept(entry, opened);
        }
    }

    /** 按像素宽度截断文本，末尾加省略号。 */
    private static String ellipsize(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String candidate = sb.toString() + text.charAt(i);
            if (font.width(candidate + "…") > maxWidth) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb.length() == 0 ? "…" : sb + "…";
    }
}
