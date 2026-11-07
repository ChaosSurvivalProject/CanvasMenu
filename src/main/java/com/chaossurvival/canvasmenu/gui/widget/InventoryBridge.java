package com.chaossurvival.canvasmenu.gui.widget;

import com.chaossurvival.canvasmenu.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

/**
 * 原版容器点击的隔离层。
 *
 * <p>画布 UI <strong>只改表现层</strong>：所有物品操作最终都通过这里转发给
 * {@code MultiPlayerGameMode} 的点击方法，由原版逻辑发送
 * {@code ServerboundContainerClickPacket}，因此生存 / 多人 / 反作弊行为完全不变。</p>
 *
 * <p>采用反射是因为 {@code gameMode} 的点击方法签名在 1.21.x 小版本间可能变动；
 * 隔离到一处后，其余 UI 代码无需跟着改。反射失败只记 debug 日志并静默放弃操作，
 * <strong>绝不自行伪造网络包</strong>。</p>
 */
public final class InventoryBridge {

    private static volatile Method cachedMethod;

    private InventoryBridge() {
    }

    /**
     * 转发一次原版容器点击。
     *
     * @param menu   容器菜单
     * @param slot   目标槽位
     * @param button 鼠标键（0 左 / 1 右）
     * @param type   点击类型
     * @return 是否成功转发
     */
    public static boolean click(AbstractContainerMenu menu, Slot slot, int button, ClickType type) {
        Minecraft client = Minecraft.getInstance();
        if (client.gameMode == null || client.player == null || menu == null || slot == null) {
            return false;
        }
        try {
            Object gameMode = client.gameMode;
            Method method = resolve(gameMode.getClass());
            if (method == null) {
                Log.debug("Vanilla click method not found; operation ignored");
                return false;
            }
            method.setAccessible(true);
            method.invoke(gameMode, menu.containerId, slot.index, button, type, client.player);
            return true;
        } catch (Throwable t) {
            Log.debug("Vanilla click bridge failed: {}", t.toString());
            return false;
        }
    }

    /**
     * 拖拽交换两个槽位：等价于原版「拾起 A → 放下到 B → 放回 A」的三次点击。
     *
     * @param menu 容器菜单
     * @param from 源槽位
     * @param to   目标槽位
     * @return 是否成功
     */
    public static boolean swap(AbstractContainerMenu menu, Slot from, Slot to) {
        if (from == null || to == null || from.index == to.index) {
            return false;
        }
        boolean first = click(menu, from, 0, ClickType.PICKUP);
        boolean second = first && click(menu, to, 0, ClickType.PICKUP);
        if (second) {
            click(menu, from, 0, ClickType.PICKUP);
        }
        return second;
    }

    /**
     * @return Shift 是否按下（用于快速移动）
     */
    public static boolean isShiftDown() {
        try {
            long window = Minecraft.getInstance().getWindow().getWindow();
            return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            Log.debug("Cannot query shift state: {}", t.toString());
            return false;
        }
    }

    /**
     * 按形状探测点击方法：前三个参数为 int，共 5 个参数。
     */
    private static Method resolve(Class<?> gameModeClass) {
        Method cached = cachedMethod;
        if (cached != null && cached.getDeclaringClass().isAssignableFrom(gameModeClass)) {
            return cached;
        }
        for (Method method : gameModeClass.getDeclaredMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 5
                    && params[0] == int.class
                    && params[1] == int.class
                    && params[2] == int.class) {
                cachedMethod = method;
                return method;
            }
        }
        return null;
    }
}
