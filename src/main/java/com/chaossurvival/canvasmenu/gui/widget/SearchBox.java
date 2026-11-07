package com.chaossurvival.canvasmenu.gui.widget;

import com.chaossurvival.canvasmenu.anim.Animator;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/**
 * 搜索框：实时过滤物品卡，<strong>120ms 防抖</strong>。
 *
 * <p>防抖计时使用 {@link Animator#now()} 的全局虚拟时钟，
 * 因此暂停 / reduce-motion 下行为一致，且不额外起线程。</p>
 */
public final class SearchBox extends AbstractWidget {

    /** 防抖时长（毫秒）。 */
    public static final long DEBOUNCE_MS = 120L;

    private static final int DEFAULT_WIDTH = 180;
    private static final int DEFAULT_HEIGHT = 24;
    private static final int MAX_LENGTH = 64;
    private static final char BACKSPACE = '\b';

    private final StringBuilder text = new StringBuilder();
    private final String placeholder;
    private final Consumer<String> onQuery;
    private long lastInputMs = -1L;
    private boolean pending;
    private boolean cursorVisible = true;
    private long cursorClockMs;

    /**
     * @param theme       主题
     * @param placeholder 占位文本
     * @param onQuery     防抖后的查询回调
     */
    public SearchBox(Theme theme, String placeholder, Consumer<String> onQuery) {
        super(theme, 0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        this.placeholder = placeholder;
        this.onQuery = onQuery;
    }

    /**
     * @return 当前输入
     */
    public String text() {
        return text.toString();
    }

    /** 清空输入。 */
    public void clear() {
        text.setLength(0);
        pending = false;
        if (onQuery != null) {
            onQuery.accept("");
        }
    }

    @Override
    public void tick() {
        cursorClockMs += Animator.TICK_MS;
        if (cursorClockMs >= 500L) {
            cursorClockMs = 0L;
            cursorVisible = !cursorVisible;
        }
        if (pending && Animator.now() - lastInputMs >= DEBOUNCE_MS) {
            pending = false;
            if (onQuery != null) {
                onQuery.accept(text.toString());
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int radius = theme.radius(0);
        Render2D.strokeRoundRect(graphics, x, y, width, height, radius, 1,
                isFocused() ? theme.accent().main() : theme.bg().floating(),
                theme.bg().panel());
        Font font = Minecraft.getInstance().font;
        String shown = text.toString();
        int textColor = theme.text().primary();
        if (shown.isEmpty()) {
            shown = placeholder;
            textColor = theme.text().secondary();
        }
        int textY = y + (height - font.lineHeight) / 2;
        Render2D.text(graphics, font, shown, x + theme.gap(1), textY, textColor);
        if (isFocused() && cursorVisible) {
            int caretX = x + theme.gap(1) + font.width(shown);
            graphics.fill(caretX, textY, caretX + 1, textY + font.lineHeight, theme.accent().main());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return contains(mouseX, mouseY);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (!isFocused()) {
            return false;
        }
        if (character == BACKSPACE) {
            if (text.length() > 0) {
                text.deleteCharAt(text.length() - 1);
                markDirty();
            }
            return true;
        }
        if (!Character.isISOControl(character) && text.length() < MAX_LENGTH) {
            text.append(character);
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) {
            return false;
        }
        if (keyCode == 259) { // Backspace
            return charTyped(BACKSPACE, modifiers);
        }
        if (keyCode == 261 && text.length() > 0) { // Delete 清空尾部
            text.deleteCharAt(text.length() - 1);
            markDirty();
            return true;
        }
        return false;
    }

    private void markDirty() {
        lastInputMs = Animator.now();
        pending = true;
    }
}
