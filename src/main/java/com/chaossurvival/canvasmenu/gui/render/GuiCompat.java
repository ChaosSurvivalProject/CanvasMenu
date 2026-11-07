package com.chaossurvival.canvasmenu.gui.render;

import com.chaossurvival.canvasmenu.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Minecraft 1.21.8+ GUI 渲染兼容层。
 *
 * <p><strong>为什么需要这一层</strong>：从 1.21.8 起，Mojang 把 GUI 的 2D 变换栈
 * 从 {@code PoseStack} 换成了 {@code Matrix3x2fStack} —— {@code pose()} 的返回类型、
 * {@code pushPose()/popPose()} 与 {@code translate(x,y,z)} / {@code scale(x,y,z)} 的
 * 参数个数全部变了；1.21.9 又把 {@code ResourceLocation} 改名为 {@code Identifier}。
 * 这些差异会让"照着旧教程写"的模组在 1.21.11 上直接编译失败。</p>
 *
 * <p>本类把易变的部分集中为<strong>一次性反射能力探测 + 缓存</strong>：编译期只依赖
 * 稳定的 {@link GuiGraphics} / {@link Font} / {@link Minecraft} 类型，运行时按目标版本
 * 自动选择可用重载；探测不到就<strong>安全降级</strong>（不做变换 / 用 classpath 资源 /
 * 用默认行高），绝不因为版本差异抛异常或让界面空白。</p>
 *
 * <p>稳定的 {@code fill(int,int,int,int,int)} 与 {@code enableScissor/disableScissor}
 * 保持直接调用，不引入额外开销。</p>
 */
public final class GuiCompat {

    /** 默认字体行高（反射失败时的兜底值）。 */
    public static final int DEFAULT_LINE_HEIGHT = 9;

    private static final Method DRAW_STRING_6;
    private static final Method DRAW_STRING_5;
    private static final Field LINE_HEIGHT_FIELD;
    private static final Method POSE;
    private static final Method PUSH;
    private static final Method POP;
    private static final Method TRANSLATE_2;
    private static final Method TRANSLATE_3;
    private static final Method SCALE_2;
    private static final Method SCALE_3;
    private static final Method GET_RESOURCE_MANAGER;
    private static final Method GET_RESOURCE;
    private static final Method RESOURCE_OPEN;
    private static final Class<?> IDENTIFIER_CLASS;
    private static final Method IDENTIFIER_FACTORY;
    private static final String IDENTIFIER_FACTORY_MODE;

    static {
        // --- drawString：1.21.6+ 文本颜色改为 ARGB，优先 6 参（带 shadow），兼容 5 参 ---
        DRAW_STRING_6 = method(GuiGraphics.class, "drawString",
                Font.class, String.class, int.class, int.class, int.class, boolean.class);
        DRAW_STRING_5 = method(GuiGraphics.class, "drawString",
                Font.class, String.class, int.class, int.class, int.class);

        // --- Font.lineHeight：不同映射里可见性/名字可能变化，反射读取 ---
        Field field = null;
        try {
            field = Font.class.getDeclaredField("lineHeight");
            field.setAccessible(true);
        } catch (Throwable ignored) {
            try {
                field = Font.class.getField("lineHeight");
            } catch (Throwable ignored2) {
                field = null;
            }
        }
        LINE_HEIGHT_FIELD = field;

        // --- pose() 变换栈：同时兼容 Matrix3x2fStack（1.21.8+）与 PoseStack（更早） ---
        POSE = method(GuiGraphics.class, "pose");
        Class<?> stackType = POSE == null ? null : POSE.getReturnType();
        Method pushMatrix = stackType == null ? null : method(stackType, "pushMatrix");
        Method pushPose = stackType == null ? null : method(stackType, "pushPose");
        Method popMatrix = stackType == null ? null : method(stackType, "popMatrix");
        Method popPose = stackType == null ? null : method(stackType, "popPose");
        PUSH = pushMatrix != null ? pushMatrix : pushPose;
        POP = popMatrix != null ? popMatrix : popPose;
        TRANSLATE_2 = stackType == null ? null : method(stackType, "translate", float.class, float.class);
        TRANSLATE_3 = stackType == null ? null : method(stackType, "translate",
                float.class, float.class, float.class);
        SCALE_2 = stackType == null ? null : method(stackType, "scale", float.class, float.class);
        SCALE_3 = stackType == null ? null : method(stackType, "scale",
                float.class, float.class, float.class);

        // --- ResourceManager：优先让资源包可以覆盖我们的贴图 ---
        GET_RESOURCE_MANAGER = method(Minecraft.class, "getResourceManager");
        Class<?> managerType = GET_RESOURCE_MANAGER == null ? null : GET_RESOURCE_MANAGER.getReturnType();
        Class<?> identifier = firstClass("net.minecraft.resources.Identifier",
                "net.minecraft.resources.ResourceLocation");
        IDENTIFIER_CLASS = identifier;
        GET_RESOURCE = managerType == null || identifier == null
                ? null : method(managerType, "getResource", identifier);
        Class<?> resourceType = GET_RESOURCE == null ? null : GET_RESOURCE.getReturnType();
        RESOURCE_OPEN = resourceType == null ? null : method(resourceType, "open");

        Method factory = null;
        String mode = "";
        if (identifier != null) {
            factory = method(identifier, "fromNamespaceAndPath", String.class, String.class);
            if (factory != null && Modifier.isStatic(factory.getModifiers())) {
                mode = "ns";
            } else {
                factory = method(identifier, "parse", String.class);
                if (factory != null && Modifier.isStatic(factory.getModifiers())) {
                    mode = "parse";
                } else {
                    factory = null;
                    try {
                        identifier.getConstructor(String.class);
                        mode = "ctor";
                    } catch (Throwable ignored) {
                        mode = "";
                    }
                }
            }
        }
        IDENTIFIER_FACTORY = factory;
        IDENTIFIER_FACTORY_MODE = mode;

        Log.debug("GuiCompat probe: drawString6={}, pose={}, transform={}, resourceManager={}",
                DRAW_STRING_6 != null, POSE != null, PUSH != null && POP != null,
                GET_RESOURCE_MANAGER != null && GET_RESOURCE != null);
    }

    private GuiCompat() {
    }

    // ------------------------------------------------------------------
    // 稳定 API（直接调用，1.21.11 官方文档确认存在）
    // ------------------------------------------------------------------

    /**
     * 实心矩形。
     *
     * @param g     GUI 上下文
     * @param x1    左
     * @param y1    上
     * @param x2    右（不含）
     * @param y2    下（不含）
     * @param color ARGB 颜色
     */
    public static void fill(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
    }

    /**
     * 启用裁剪区（卡片流式区域滚动时防止越界绘制）。
     *
     * @param g GUI 上下文
     * @param x1 左
     * @param y1 上
     * @param x2 右
     * @param y2 下
     */
    public static void enableScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    /** @param g GUI 上下文 关闭裁剪区 */
    public static void disableScissor(GuiGraphics g) {
        g.disableScissor();
    }

    // ------------------------------------------------------------------
    // 文本与字体
    // ------------------------------------------------------------------

    /**
     * 绘制文本，自动适配 6 参 / 5 参 {@code drawString} 重载。
     *
     * <p>1.21.6+ 颜色参数必须是 ARGB（含 alpha），{@link Render2D} 传入的颜色
     * 已由 {@code Theme} 保证带 alpha 通道。</p>
     *
     * @param g     GUI 上下文
     * @param font  字体
     * @param text  文本
     * @param x     x
     * @param y     y
     * @param color ARGB 颜色
     */
    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
        if (g == null || font == null || text == null || text.isEmpty()) {
            return;
        }
        if (DRAW_STRING_6 != null) {
            invoke(DRAW_STRING_6, g, font, text, x, y, color, false);
            return;
        }
        if (DRAW_STRING_5 != null) {
            invoke(DRAW_STRING_5, g, font, text, x, y, color);
        }
    }

    /**
     * @param font 字体
     * @return 行高（反射失败回退 9）
     */
    public static int lineHeight(Font font) {
        if (font == null || LINE_HEIGHT_FIELD == null) {
            return DEFAULT_LINE_HEIGHT;
        }
        try {
            Object value = LINE_HEIGHT_FIELD.get(font);
            return value instanceof Number number ? Math.max(1, number.intValue()) : DEFAULT_LINE_HEIGHT;
        } catch (Throwable ignored) {
            return DEFAULT_LINE_HEIGHT;
        }
    }

    // ------------------------------------------------------------------
    // 2D 变换（进入动画的 0.98→1.0 缩放）
    // ------------------------------------------------------------------

    /**
     * 以 {@code (cx, cy)} 为中心整体缩放绘制。
     *
     * <p>运行时自动选择 {@code pushMatrix/translate(x,y)/scale(x,y)}（1.21.8+）
     * 或 {@code pushPose/translate(x,y,z)/scale(x,y,z)}（更早版本）。
     * 探测不到可用变换栈时<strong>直接执行绘制</strong>：只是少了轻微缩放，
     * 界面依旧完整可用，不会产生黑屏或异常。</p>
     *
     * @param g     GUI 上下文
     * @param cx    缩放中心 x
     * @param cy    缩放中心 y
     * @param scale 缩放系数
     * @param body  需要被变换包裹的绘制逻辑
     */
    public static void withTransform(GuiGraphics g, float cx, float cy, float scale, Runnable body) {
        if (body == null) {
            return;
        }
        if (POSE == null || PUSH == null || POP == null
                || (TRANSLATE_2 == null && TRANSLATE_3 == null)) {
            body.run();
            return;
        }
        Object stack = invoke(POSE, g);
        if (stack == null) {
            body.run();
            return;
        }
        boolean pushed = false;
        try {
            invoke(PUSH, stack);
            pushed = true;
            translate(stack, cx, cy);
            scale(stack, scale, scale);
            translate(stack, -cx, -cy);
            body.run();
        } catch (Throwable t) {
            Log.debug("GUI transform failed, drawing without transform: {}", t.toString());
        } finally {
            if (pushed) {
                try {
                    invoke(POP, stack);
                } catch (Throwable ignored) {
                    // 栈恢复失败不应影响后续绘制
                }
            }
        }
    }

    private static void translate(Object stack, float x, float y) {
        if (TRANSLATE_2 != null) {
            invoke(TRANSLATE_2, stack, x, y);
        } else if (TRANSLATE_3 != null) {
            invoke(TRANSLATE_3, stack, x, y, 0.0f);
        }
    }

    private static void scale(Object stack, float x, float y) {
        if (SCALE_2 != null) {
            invoke(SCALE_2, stack, x, y);
        } else if (SCALE_3 != null) {
            invoke(SCALE_3, stack, x, y, 1.0f);
        }
    }

    // ------------------------------------------------------------------
    // 资源读取（ResourceManager 优先，classpath 兜底）
    // ------------------------------------------------------------------

    /**
     * 打开一个模组资源。
     *
     * <p>优先走 Minecraft {@code ResourceManager}，这样<strong>资源包可以覆盖贴图</strong>；
     * 反射不可用时回退到 classpath（jar 内置资源）。调用方负责关闭流。</p>
     *
     * @param path 相对游戏资源根的路径，例如 {@code assets/canvasmenu/textures/commands/weather/weather_sun.png}
     * @return 输入流；资源不存在时返回 {@code null}
     */
    public static InputStream openResource(String path) {
        InputStream fromManager = viaResourceManager(path);
        if (fromManager != null) {
            return fromManager;
        }
        try {
            return GuiCompat.class.getResourceAsStream("/" + path);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static InputStream viaResourceManager(String path) {
        if (GET_RESOURCE_MANAGER == null || GET_RESOURCE == null || RESOURCE_OPEN == null
                || IDENTIFIER_CLASS == null || IDENTIFIER_FACTORY_MODE.isEmpty()) {
            return null;
        }
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                return null;
            }
            Object manager = GET_RESOURCE_MANAGER.invoke(client);
            if (manager == null) {
                return null;
            }
            // assets/<namespace>/... → namespace = canvasmenu, 其余为相对路径
            String relative = path.startsWith("assets/canvasmenu/")
                    ? path.substring("assets/canvasmenu/".length()) : path;
            Object identifier = makeIdentifier(relative);
            if (identifier == null) {
                return null;
            }
            Object optional = GET_RESOURCE.invoke(manager, identifier);
            if (optional == null) {
                return null;
            }
            Object resource;
            if (optional instanceof java.util.Optional<?> opt) {
                resource = opt.orElse(null);
            } else {
                resource = optional;
            }
            if (resource == null) {
                return null;
            }
            Object stream = RESOURCE_OPEN.invoke(resource);
            return stream instanceof InputStream in ? in : null;
        } catch (Throwable t) {
            Log.debug("ResourceManager lookup unavailable for {}: {}", path, t.toString());
            return null;
        }
    }

    private static Object makeIdentifier(String relative) throws ReflectiveOperationException {
        return switch (IDENTIFIER_FACTORY_MODE) {
            case "ns" -> IDENTIFIER_FACTORY.invoke(null, "canvasmenu", relative);
            case "parse" -> IDENTIFIER_FACTORY.invoke(null, "canvasmenu:" + relative);
            case "ctor" -> IDENTIFIER_CLASS.getConstructor(String.class)
                    .newInstance("canvasmenu:" + relative);
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // 反射工具
    // ------------------------------------------------------------------

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod(name, parameters);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class<?> firstClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {
                // 尝试下一个候选类名
            }
        }
        return null;
    }

    /**
     * 调用一个方法；失败返回 {@code null}，绝不向外抛异常。
     *
     * <p>注意：void 方法也返回 {@code null}，因此调用方不应以返回值判断"是否成功"，
     * 而是依赖本层的降级策略。</p>
     *
     * @param m     方法
     * @param target 目标对象（静态方法传 {@code null}）
     * @param args  参数
     * @return 返回值，或 {@code null}
     */
    private static Object invoke(Method m, Object target, Object... args) {
        if (m == null) {
            return null;
        }
        try {
            return m.invoke(target, args);
        } catch (Throwable t) {
            Log.debug("Reflective GUI call failed: {}", t.toString());
            return null;
        }
    }
}
