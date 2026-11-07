package com.chaossurvival.canvasmenu;

import com.chaossurvival.canvasmenu.anim.Animator;
import com.chaossurvival.canvasmenu.config.ConfigManager;
import com.chaossurvival.canvasmenu.gui.screen.CanvasInventoryScreen;
import com.chaossurvival.canvasmenu.gui.screen.SettingsScreen;
import com.chaossurvival.canvasmenu.util.Async;
import com.chaossurvival.canvasmenu.util.Log;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 画布背包的客户端入口 {@link ClientModInitializer}。
 *
 * <p>职责：配置 / 主题 / 动效初始化、{@code F6} 设置键、{@code E} 背包接管。
 * 「指令中心」模块由并列的第二个入口
 * {@code com.canvasmenu.CommandCenter} 负责；GitHub 自动更新链路也统一由
 * {@code com.canvasmenu.update.AutoUpdater} 驱动（单一链路，避免重复网络请求）。</p>
 *
 * <p>接管完全在客户端表现层：底层仍是原版 {@code Menu/Slot} 与原版网络包，
 * 不修改服务端协议，多人 / 生存行为不变。</p>
 */
public final class CanvasMenu implements ClientModInitializer {

    /** 模组标识。 */
    public static final String MOD_ID = "canvasmenu";

    private static KeyMapping settingsKey;

    @Override
    public void onInitializeClient() {
        ConfigManager.init();
        Animator.setReduceMotion(ConfigManager.get().reduceMotion());

        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "canvasmenu.key.settings",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "canvasmenu.key.category"));

        ClientTickEvents.END_CLIENT_TICK.register(CanvasMenu::onClientTick);
        Runtime.getRuntime().addShutdownHook(new Thread(Async::shutdown, "canvasmenu-async-shutdown"));
        Log.info("Canvas Menu {} initialized", localVersionString());
    }

    /**
     * 每客户端 tick：处理设置键与 {@code E} 背包接管。
     *
     * @param client Minecraft 客户端实例
     */
    private static void onClientTick(Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (settingsKey.consumeClick()) {
            client.setScreen(new SettingsScreen(client.screen, ConfigManager.theme()));
            return;
        }
        if (ConfigManager.get().takeoverInventory() && client.options.keyInventory.consumeClick()) {
            // 覆盖原版背包 Screen；底层仍使用 player.inventoryMenu，不改协议
            client.setScreen(new CanvasInventoryScreen(client.screen, ConfigManager.theme(),
                    client.player.inventoryMenu));
        }
    }

    /**
     * 读取 {@code fabric.mod.json} 的版本作为本地版本。
     *
     * @return 版本字符串
     */
    public static String localVersionString() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }
}
