package com.chaossurvival.canvasmenu.gui.widget;

import com.chaossurvival.canvasmenu.gui.layout.Flex;
import com.chaossurvival.canvasmenu.gui.render.Render2D;
import com.chaossurvival.canvasmenu.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/**
 * 右键详情浮层（Context Layer 区域）。
 *
 * <p>展示物品名、数量与详情，并提供「使用 / 丢弃 / 关闭」三个动作；
 * 物品操作仍经 {@link InventoryBridge} 走原版点击包。</p>
 */
public final class ContextLayer extends AbstractWidget {

    private static final int DEFAULT_WIDTH = 180;
    private static final int DEFAULT_HEIGHT = 116;
    private static final int BUTTON_HEIGHT = 22;

    private final ItemCard source;
    private final Runnable onClose;
    private final List<Button> actions = new ArrayList<>();
    private boolean open;

    /**
     * @param theme   主题
     * @param source  触发浮层的卡片
     * @param onClose 关闭回调
     */
    public ContextLayer(Theme theme, ItemCard source, Runnable onClose) {
        super(theme, 0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        this.source = source;
        this.onClose = onClose;
        this.actions.add(new Button(theme, label("canvasmenu.card.context.use"),
                () -> InventoryBridge.click(sourceSlotMenu(), source.slot(), 0, ClickType.PICKUP)));
        this.actions.add(new Button(theme, label("canvasmenu.card.context.drop"),
                () -> InventoryBridge.click(sourceSlotMenu(), source.slot(), 0, ClickType.THROW)));
        this.actions.add(new Button(theme, label("canvasmenu.settings.cancel"), this::close));
    }

    /**
     * @return 是否显示
     */
    public boolean isOpen() {
        return open;
    }

    /** 显示浮层。 */
    public void open() {
        this.open = true;
    }

    /** 关闭浮层。 */
    public void close() {
        this.open = false;
        if (onClose != null) {
            onClose.run();
        }
    }

    /**
     * 由 Screen 在布局时调用，确定浮层位置与内部按钮位置。
     *
     * @param x 左上 x
     * @param y 左上 y
     */
    public void place(int x, int y) {
        setBounds(x, y, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        int gap = theme.gap(1);
        int innerWidth = DEFAULT_WIDTH - 2 * gap;
        for (Button button : actions) {
            button.setBounds(x + gap, 0, innerWidth, BUTTON_HEIGHT);
        }
        List<Button> buttons = new ArrayList<>(actions);
        new Flex(Flex.Direction.COLUMN, 1, false)
                .layout(com.chaossurvival.canvasmenu.gui.layout.Region.of(
                                com.chaossurvival.canvasmenu.gui.layout.Region.Kind.CONTEXT,
                                x + gap, y + DEFAULT_HEIGHT / 3, innerWidth, DEFAULT_HEIGHT * 2 / 3),
                        buttons, theme);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!open) {
            return;
        }
        int radius = theme.radius(2);
        Render2D.dropShadow(graphics, x, y, width, height, radius, theme.elevation(3), 3, 2);
        Render2D.nineSlice(graphics, x, y, width, height,
                Render2D.Insets.uniform(theme.gap(1)),
                theme.bg().floating(), theme.accent().main());

        Font font = Minecraft.getInstance().font;
        String title = source.stack().isEmpty()
                ? label("canvasmenu.card.empty")
                : source.stack().getHoverName().getString();
        Render2D.text(graphics, font, title, x + theme.gap(1), y + theme.gap(1),
                theme.text().primary());
        String detail = label("canvasmenu.card.context.details") + ": " + source.stack().getCount();
        Render2D.text(graphics, font, detail, x + theme.gap(1),
                y + theme.gap(1) + font.lineHeight + theme.gap(0), theme.text().secondary());

        for (Button button : actions) {
            button.setHovered(button.contains(mouseX, mouseY));
            button.render(graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean contains(double px, double py) {
        return open && super.contains(px, py);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) {
            return false;
        }
        for (Button action : actions) {
            if (action.contains(mouseX, mouseY) && action.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (!contains(mouseX, mouseY)) {
            close();
            return true;
        }
        return true;
    }

    private net.minecraft.world.inventory.AbstractContainerMenu sourceSlotMenu() {
        return source.menu();
    }

    private static String label(String key) {
        return Component.translatable(key).getString();
    }
}
