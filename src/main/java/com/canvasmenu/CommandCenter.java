package com.canvasmenu;

import com.chaossurvival.canvasmenu.anim.Animator;
import com.chaossurvival.canvasmenu.config.ConfigManager;
import com.chaossurvival.canvasmenu.util.Log;
import com.canvasmenu.command.CommandCategory;
import com.canvasmenu.command.CommandEntry;
import com.canvasmenu.command.CommandRegistry;
import com.canvasmenu.command.Favorites;
import com.canvasmenu.render.IconTextures;
import com.canvasmenu.screen.CommandPanelScreen;
import com.canvasmenu.update.AutoUpdater;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 「指令中心」模块的客户端入口（第二个 {@link ClientModInitializer}）。
 *
 * <p>与画布背包入口 {@code com.chaossurvival.canvasmenu.CanvasMenu} 并列注册在
 * {@code fabric.mod.json} 的 {@code entrypoints.client} 中，职责单一：</p>
 * <ol>
 *   <li>加载 {@code commands.json} 与收藏夹；</li>
 *   <li>在<strong>虚拟线程</strong>里异步预加载全部指令贴图（不卡启动）；</li>
 *   <li>注册 {@code F7} 快捷键打开 {@link CommandPanelScreen}；</li>
 *   <li>启动 GitHub Release 更新检查、注册退出时的 jar 替换；</li>
 *   <li>把「指令中心」入口按钮挂到画布主菜单（见 {@code CanvasInventoryScreen}）。</li>
 * </ol>
 *
 * <p>{@link ConfigManager#init()} 在这里与背包入口各调一次是<strong>幂等</strong>的
 * （只是重读磁盘配置），这样无论 Fabric 先初始化哪个 entrypoint，
 * 主题 / 配置 / reduce-motion 都已经就绪。</p>
 */
public final class CommandCenter implements ClientModInitializer {

    /** 模组 id，与 {@code fabric.mod.json} 一致。 */
    public static final String MOD_ID = "canvasmenu";

    private static KeyMapping commandKey;

    @Override
    public void onInitializeClient() {
        // 配置 / 主题（幂等）
        ConfigManager.init();
        Animator.setReduceMotion(ConfigManager.get().reduceMotion());

        // JSON 指令注册表 + 收藏
        int count = CommandRegistry.reload();
        Favorites.init();

        // 异步贴图预加载：虚拟线程，主线程不等待
        IconTextures.preload(collectIconPaths());

        // 快捷键：F7 打开指令中心
        // 1.21.9+ 的 KeyMapping 第四参为 KeyMapping.Category，不再是分类翻译键
        commandKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "canvasmenu.key.command_center",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(CommandCenter::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(
                client -> AutoUpdater.onClientStopping());

        // 启动时检查更新（配置可关闭）；失败静默降级
        if (ConfigManager.get().checkUpdatesOnStartup()) {
            AutoUpdater.checkOnStartup();
        }
        AutoUpdater.pendingMessage().ifPresent(text -> Log.info("{}", text));

        Log.info("Command Center ready: {} commands, {} categories",
                count, CommandRegistry.categories().size());
    }

    private static void onClientTick(Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (commandKey.consumeClick()) {
            open();
        }
    }

    /**
     * 打开指令中心面板（保留当前 Screen 作为返回目标）。
     * 主菜单的入口按钮与 F7 快捷键都调用这里。
     */
    public static void open() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        Screen current = client.screen;
        client.setScreen(new CommandPanelScreen(current, ConfigManager.theme()));
    }

    private static List<String> collectIconPaths() {
        List<String> paths = new ArrayList<>();
        for (CommandEntry entry : CommandRegistry.all()) {
            if (!entry.icon().isBlank()) {
                paths.add(entry.icon());
            }
        }
        for (CommandCategory category : CommandRegistry.categories()) {
            if (!category.icon().isBlank()) {
                paths.add(category.icon());
            }
        }
        return paths;
    }
}
