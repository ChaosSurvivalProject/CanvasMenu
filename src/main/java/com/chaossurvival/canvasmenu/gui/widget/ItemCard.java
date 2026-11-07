package com.chaossurvival.canvasmenu.gui.widget;

import com.chaossurvival.canvasmenu.anim.Tween;
import com.chaossurvival.canvasmenu.anim.transitions.Transition;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * 物品卡片（替代原版 Slot 的视觉表现）。
 *
 * <p>图标居中漂浮、<strong>无格子边框</strong>；hover 抬升 + 光晕 + 名称/数量从左下展开；
 * 稀有度用<strong>描边语言与卡面微染</strong>而非彩色格子底；装备 / 材料 / 方块用不同轮廓；
 * 右键弹出 {@link ContextLayer}，拖拽通过 {@link InventoryBridge} 对接原版 Slot。</p>
 */
public final class ItemCard extends AbstractWidget {

    /** 卡片轮廓形态。 */
    public enum Shape {
        /** 圆角 pill，用于装备。 */
        PILL,
        /** 切角，用于材料。 */
        CUT_CORNER,
        /** 六边，用于方块。 */
        HEX
    }

    /** 卡片默认边长。 */
    public static final int DEFAULT_SIZE = 48;
    /** 物品图标边长。 */
    private static final int ICON_SIZE = 16;
    /** hover 抬升像素。 */
    private static final int LIFT_PX = 2;
    private static final int GLOW_LAYERS = 3;
    private static final int SHADOW_LAYERS = 2;
    private static final int OUTLINE_THICKNESS = 1;

    private final ItemStack stack;
    private final Slot slot;
    private final AbstractContainerMenu menu;
    private final Consumer<ItemCard> onContext;
    private final Tween expand;
    private final Tween lift;
    private Shape shape = Shape.PILL;
    private boolean dragging;
    private int dragButton;

    /**
     * @param theme     主题
     * @param stack     物品堆（允许 {@code null}，视为空槽）
     * @param slot      对应原版槽位（可为 {@code null}）
     * @param menu      容器菜单
     * @param onContext 右键详情回调
     */
    public ItemCard(Theme theme, ItemStack stack, Slot slot, AbstractContainerMenu menu,
                    Consumer<ItemCard> onContext) {
        super(theme, 0, 0, DEFAULT_SIZE, DEFAULT_SIZE);
        this.stack = stack == null ? ItemStack.EMPTY : stack;
        this.slot = slot;
        this.menu = menu;
        this.onContext = onContext;
        this.expand = Tween.of(0.0f, 0.0f, 0L, Transition.CARD_EXPAND.easing());
        this.lift = Tween.of(0.0f, 0.0f, 0L, Transition.HOVER.easing());
    }

    /**
     * @param newShape 轮廓形态
     */
    public void setShape(Shape newShape) {
        this.shape = newShape;
    }

    /**
     * @return 轮廓形态
     */
    public Shape shape() {
        return shape;
    }

    /**
     * @return 物品堆
     */
    public ItemStack stack() {
        return stack;
    }

    /**
     * @return 原版槽位
     */
    public Slot slot() {
        return slot;
    }

    /**
     * @return 所属容器菜单
     */
    public AbstractContainerMenu menu() {
        return menu;
    }

    /**
     * @return 是否正在拖拽
     */
    public boolean isDragging() {
        return dragging;
    }

    /**
     * @return 拖拽使用的鼠标键
     */
    public int dragButton() {
        return dragButton;
    }

    /** 结束拖拽（由 Screen 在 mouseReleased 时调用）。 */
    public void endDrag() {
        dragging = false;
    }

    @Override
    public void setHovered(boolean isHovered) {
        if (isHovered == this.isHovered()) {
            return;
        }
        super.setHovered(isHovered);
        float target = isHovered ? 1.0f : 0.0f;
        expand.retarget(target,
                Transition.CARD_EXPAND.millis(com.chaossurvival.canvasmenu.anim.Animator.isReduceMotion()),
                Transition.CARD_EXPAND.easing());
        lift.retarget(target,
                Transition.HOVER.millis(com.chaossurvival.canvasmenu.anim.Animator.isReduceMotion()),
                Transition.HOVER.easing());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        float expansion = expand.get();
        int liftPixels = (int) (LIFT_PX * lift.get());
        int top = y - liftPixels;
        int radius = theme.radius(1);
        int face = Render2D.lerp(theme.bg().panel(), theme.bg().floating(), 0.55f);

        Render2D.dropShadow(graphics, x, top, width, height, radius,
                theme.elevation(2), SHADOW_LAYERS, 1);
        if (expansion > 0.01f || isFocused()) {
            Render2D.glowRect(graphics, x, top, width, height, radius,
                    theme.accent().glow(), GLOW_LAYERS);
        }
        renderShape(graphics, x, top, width, height, face);
        int outline = rarityOutline();
        Render2D.strokeRoundRect(graphics, x, top, width, height, radius,
                OUTLINE_THICKNESS, outline, face);

        if (!stack.isEmpty()) {
            graphics.renderItem(stack,
                    centerX() - ICON_SIZE / 2,
                    centerY() - ICON_SIZE / 2 - liftPixels);
        }

        if (expansion > 0.01f) {
            int reveal = (int) (width * expansion);
            graphics.enableScissor(x, top, x + reveal, top + height);
            Font font = Minecraft.getInstance().font;
            int textY = top + height - font.lineHeight - theme.gap(0);
            Render2D.text(graphics, font, stack.getHoverName().getString(),
                    x + theme.gap(0), textY, theme.text().primary());
            if (stack.getCount() > 1) {
                String count = String.valueOf(stack.getCount());
                Render2D.text(graphics, font, count,
                        x + width - font.width(count) - theme.gap(0),
                        top + theme.gap(0), theme.text().primary());
            }
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (button == 1) { // 右键：详情浮层
            if (onContext != null) {
                onContext.accept(this);
            }
            return true;
        }
        dragging = true;
        dragButton = button;
        ClickType type = InventoryBridge.isShiftDown() ? ClickType.QUICK_MOVE : ClickType.PICKUP;
        InventoryBridge.click(menu, slot, button, type);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return dragging;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257) { // Enter：等价于左键拾取
            return mouseClicked(centerX(), centerY(), 0);
        }
        return false;
    }

    /**
     * 稀有度用描边色表达（低饱和，不用彩色格子底）。
     */
    private int rarityOutline() {
        String rarity = String.valueOf(stack.getRarity()).toUpperCase(Locale.ROOT);
        if (rarity.contains("EPIC")) {
            return 0x99B98CFF;
        }
        if (rarity.contains("RARE")) {
            return 0x996FB7FF;
        }
        if (rarity.contains("UNCOMMON")) {
            return 0x99E8D36B;
        }
        return stack.isEmpty() ? theme.bg().floating() : theme.text().secondary();
    }

    /**
     * 三类轮廓：pill / 切角 / 六边。
     */
    private void renderShape(GuiGraphics graphics, int left, int top, int w, int h, int color) {
        switch (shape) {
            case PILL -> Render2D.roundRect(graphics, left, top, w, h, h / 2, color);
            case CUT_CORNER -> {
                Render2D.roundRect(graphics, left, top, w, h, theme.radius(0), color);
                int cut = Math.max(2, Math.min(w, h) / 5);
                int background = theme.bg().base();
                for (int i = 0; i < cut; i++) {
                    graphics.fill(left + i, top, left + i + 1, top + cut - i, background);
                    graphics.fill(left + w - cut + i, top + h - i - 1,
                            left + w - cut + i + 1, top + h, background);
                }
            }
            case HEX -> {
                int bevel = Math.max(2, w / 6);
                for (int dy = 0; dy < h; dy++) {
                    float relative = Math.abs((dy + 0.5f) / (float) h - 0.5f) * 2.0f;
                    int inset = (int) (bevel * relative);
                    graphics.fill(left + inset, top + dy, left + w - inset, top + dy + 1, color);
                }
            }
        }
    }
}
