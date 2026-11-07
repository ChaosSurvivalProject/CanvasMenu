package com.canvasmenu.command;

import com.chaossurvival.canvasmenu.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 把指令<strong>填入聊天栏，但不自动发送</strong>。
 *
 * <p>需求里「点击后执行」在详细交互约束中被明确为「打开聊天栏并填入指令、
 * 让玩家确认后回车」，这里以后者为准 —— 这是更安全的行为：玩家可以看清指令、
 * 修改参数、或按 Esc 取消，避免误触 {@code /gamerule} / {@code /summon} 之类
 * 影响世界的指令。</p>
 *
 * <p>实现方式是打开原版 {@code ChatScreen} 并把指令作为初始文本传入：
 * 原版聊天输入框获得焦点、光标停在末尾，玩家按回车才真正发送。
 * 由于 1.21.x 中 {@code ChatScreen} 的构造与输入框字段名多次变动，
 * 这里用一次性反射探测，按版本优先级依次尝试：</p>
 * <ol>
 *   <li>{@code ChatScreen(String initial, boolean isDraft)} —— 1.21.9+ 真实签名，
 *       {@code isDraft} 传 {@code false}：我们填入的是一条新指令，不是恢复草稿；</li>
 *   <li>{@code ChatScreen(String initial)} —— 更早版本；</li>
 *   <li>无参构造 + 反射写输入框；</li>
 *   <li>全部不可用才兜底把指令复制到系统剪贴板。</li>
 * </ol>
 * <p>编译期只依赖稳定的 {@link Screen} / {@link Minecraft}，不引用
 * 任何 1.21.x 版本中变动过的 {@code ChatScreen} 类型。</p>
 */
public final class ChatBridge {

    private static final Class<?> CHAT_SCREEN;
    /** 1.21.9+：ChatScreen(String initial, boolean isDraft)。 */
    private static final Constructor<?> CTOR_WITH_TEXT_DRAFT;
    private static final Constructor<?> CTOR_WITH_TEXT;
    private static final Constructor<?> CTOR_EMPTY;
    private static final Method SET_CLIPBOARD;
    private static final Field KEYBOARD_HANDLER;

    static {
        Class<?> clazz = null;
        try {
            clazz = Class.forName("net.minecraft.client.gui.screens.ChatScreen");
        } catch (Throwable ignored) {
            // 类名在少数映射里可能不同，走剪贴板兜底
        }
        CHAT_SCREEN = clazz;

        Constructor<?> withTextDraft = null;
        Constructor<?> withText = null;
        Constructor<?> empty = null;
        if (clazz != null) {
            try {
                withTextDraft = clazz.getConstructor(String.class, boolean.class);
            } catch (Throwable ignored) {
                // 不是 1.21.9+，继续探测旧签名
            }
            try {
                withText = clazz.getConstructor(String.class);
            } catch (Throwable ignored) {
                // 尝试无参构造
            }
            try {
                empty = clazz.getConstructor();
            } catch (Throwable ignored) {
                // 三种构造都不可用
            }
        }
        CTOR_WITH_TEXT_DRAFT = withTextDraft;
        CTOR_WITH_TEXT = withText;
        CTOR_EMPTY = empty;

        Field keyboard = null;
        Method clipboard = null;
        try {
            for (Field field : Minecraft.class.getDeclaredFields()) {
                if (field.getType().getName().contains("KeyboardHandler")) {
                    field.setAccessible(true);
                    keyboard = field;
                    break;
                }
            }
            if (keyboard != null) {
                clipboard = keyboard.getType().getMethod("setClipboard", String.class);
            }
        } catch (Throwable ignored) {
            clipboard = null;
        }
        KEYBOARD_HANDLER = keyboard;
        SET_CLIPBOARD = clipboard;
    }

    private ChatBridge() {
    }

    /**
     * 打开聊天栏并填入指令。
     *
     * @param command 完整指令（含 {@code /}）
     * @return {@code true} 表示已成功填入聊天栏；{@code false} 表示已复制到剪贴板，需玩家手动粘贴
     */
    public static boolean fill(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return false;
        }
        Screen chat = createChatScreen(command);
        if (chat != null) {
            client.setScreen(chat);
            return true;
        }
        boolean copied = copyToClipboard(client, command);
        Log.debug("ChatScreen unavailable; command {} to clipboard: {}",
                copied ? "copied" : "not copied", command);
        return false;
    }

    private static Screen createChatScreen(String command) {
        if (CHAT_SCREEN == null) {
            return null;
        }
        try {
            // 1.21.9+ 首选：ChatScreen(String initial, boolean isDraft)
            if (CTOR_WITH_TEXT_DRAFT != null) {
                Object screen = CTOR_WITH_TEXT_DRAFT.newInstance(command, false);
                return screen instanceof Screen cast ? cast : null;
            }
            if (CTOR_WITH_TEXT != null) {
                Object screen = CTOR_WITH_TEXT.newInstance(command);
                return screen instanceof Screen cast ? cast : null;
            }
            if (CTOR_EMPTY != null) {
                Object screen = CTOR_EMPTY.newInstance();
                writeInitialText(screen, command);
                return screen instanceof Screen cast ? cast : null;
            }
        } catch (Throwable t) {
            Log.debug("Cannot open ChatScreen: {}", t.toString());
        }
        return null;
    }

    /**
     * 无参构造分支：反射找到输入框字段并调用 {@code setValue(String)}。
     * 找得到就填，找不到也不影响返回（玩家仍可自己输入）。
     */
    private static void writeInitialText(Object screen, String command) {
        if (screen == null) {
            return;
        }
        for (Field field : screen.getClass().getDeclaredFields()) {
            String name = field.getName().toLowerCase(java.util.Locale.ROOT);
            if (!name.contains("input") && !name.contains("chat") && !name.contains("field")) {
                continue;
            }
            if (String.class.equals(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object box = field.get(screen);
                if (box == null) {
                    continue;
                }
                Method setValue = box.getClass().getMethod("setValue", String.class);
                setValue.invoke(box, command);
                return;
            } catch (Throwable ignored) {
                // 换下一个候选字段
            }
        }
    }

    private static boolean copyToClipboard(Minecraft client, String command) {
        if (KEYBOARD_HANDLER == null || SET_CLIPBOARD == null) {
            return false;
        }
        try {
            Object handler = KEYBOARD_HANDLER.get(client);
            if (handler == null) {
                return false;
            }
            SET_CLIPBOARD.invoke(handler, command);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
