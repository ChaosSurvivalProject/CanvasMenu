package com.chaossurvival.canvasmenu;

import com.chaossurvival.canvasmenu.anim.Animator;
import com.chaossurvival.canvasmenu.config.ConfigManager;
import com.chaossurvival.canvasmenu.config.ModConfig;
import com.chaossurvival.canvasmenu.gui.screen.CanvasInventoryScreen;
import com.chaossurvival.canvasmenu.gui.screen.SettingsScreen;
import com.chaossurvival.canvasmenu.update.UpdateChecker;
import com.chaossurvival.canvasmenu.update.Updater;
import com.chaossurvival.canvasmenu.util.Async;
import com.chaossurvival.canvasmenu.util.Log;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

/**
 * 客户端入口 {@link ClientModInitializer}。
 *
 * <p>负责：配置 / 主题 / 动效初始化、按键注册、{@code E} 背包接管、
 * 启动时异步更新检查、退出时完成挂起的更新替换。</p>
 *
 * <p>接管完全在客户端表现层：底层仍是原版 {@code Menu/Slot} 与原版网络包，
 * 不修改服务端协议，多人 / 生存行为不变。</p>
 */
public final class CanvasMenu implements ClientModInitializer {

    /** 模组标识。 */
    public static final String MOD_ID = "canvasmenu";

    private static KeyMapping settingsKey;
    private static volatile UpdateChecker.ReleaseInfo latestRelease;
    private static volatile boolean updateCheckRunning;
    private static volatile long lastCheckId;

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
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Updater.onClientStopping());

        Updater.pendingMessage(Path.of("mods"))
                .ifPresent(message -> Log.info("{}", message));

        if (ConfigManager.get().checkUpdatesOnStartup()) {
            checkUpdatesAsync();
        }
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
     * 触发一次异步更新检查（幂等，重复调用被忽略直到本次结束）。
     */
    public static void checkUpdatesAsync() {
        if (updateCheckRunning) {
            return;
        }
        updateCheckRunning = true;
        ModConfig config = ConfigManager.get();
        String local = localVersionString();
        UpdateChecker.check(config, local).whenComplete((result, error) -> {
            latestRelease = result == null ? null : result.orElse(null);
            lastCheckId++;
            updateCheckRunning = false;
            if (error != null) {
                Log.debug("Update check task failed: {}", error.toString());
            }
        });
    }

    /**
     * @return 最近一次检查到的可用更新，无则 {@code null}
     */
    public static UpdateChecker.ReleaseInfo latestRelease() {
        return latestRelease;
    }

    /**
     * @return 是否有检查正在进行
     */
    public static boolean isUpdateCheckRunning() {
        return updateCheckRunning;
    }

    /**
     * @return 已完成检查的次数（供 UI 判断结果是否刷新）
     */
    public static long lastCheckId() {
        return lastCheckId;
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
