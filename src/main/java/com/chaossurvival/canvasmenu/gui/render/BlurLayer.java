package com.chaossurvival.canvasmenu.gui.render;

import com.chaossurvival.canvasmenu.util.Log;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 背景模糊的<strong>安全</strong>封装。
 *
 * <p>1.21.11 上没有稳定公开的 GUI 模糊入口，直接调用原版 blur 路径存在崩溃风险。
 * 因此本类默认采用「半透明纯色」降级方案，同时保留一个可注入的 {@link BlurProvider} 扩展点：
 * 未来若某版本提供公开 API，只需注入实现即可启用，无需改动调用方。</p>
 *
 * <p>任何异常（反射失败、渲染状态异常、驱动问题）都会被捕获并降级，
 * <strong>绝不把异常抛回渲染线程</strong>。</p>
 */
public final class BlurLayer {

    /** 可注入的真实模糊实现；返回 true 表示已成功绘制模糊。 */
    @FunctionalInterface
    public interface BlurProvider {
        /**
         * @param graphics GUI 绘制上下文
         * @param x        左上 x
         * @param y        左上 y
         * @param width    宽
         * @param height   高
         * @return 是否成功绘制
         */
        boolean blur(GuiGraphics graphics, int x, int y, int width, int height);
    }

    private static volatile BlurProvider provider = null;
    private static volatile boolean probeDone = false;
    private static volatile boolean probeAvailable = false;

    private BlurLayer() {
    }

    /**
     * 注入真实模糊实现（可选）。
     *
     * @param customProvider 实现，传 {@code null} 可恢复降级模式
     */
    public static void setProvider(BlurProvider customProvider) {
        provider = customProvider;
    }

    /**
     * 尝试绘制模糊背景；失败时绘制半透明圆角降级层。
     *
     * @param graphics  GUI 绘制上下文
     * @param x         左上 x
     * @param y         左上 y
     * @param w         宽
     * @param h         高
     * @param radius    圆角半径
     * @param fallback  降级色（ARGB）
     * @return 是否真的执行了模糊
     */
    public static boolean safeBlur(GuiGraphics graphics, int x, int y, int w, int h, int radius, int fallback) {
        // 仅记录一次环境探测结果用于诊断，不改变「显式注入才启用」的保守决策
        probeAvailable();
        BlurProvider current = provider;
        if (current != null) {
            try {
                if (current.blur(graphics, x, y, w, h)) {
                    return true;
                }
            } catch (Throwable t) {
                Log.debug("Blur provider failed, falling back: {}", t.toString());
            }
        }
        Render2D.roundRect(graphics, x, y, w, h, radius, fallback);
        return false;
    }

    /**
     * 探测运行环境是否具备可用的模糊能力。
     *
     * <p>即使能加载 {@code PostChain}，也不代表存在<strong>安全</strong>的 GUI 模糊入口；
     * 保守策略是：只有显式注入了 {@link BlurProvider} 才启用模糊，其余情况一律降级。</p>
     *
     * @return 是否具备自动模糊能力
     */
    private static boolean probeAvailable() {
        if (!probeDone) {
            probeDone = true;
            try {
                Class.forName("net.minecraft.client.renderer.PostChain");
            } catch (Throwable t) {
                Log.debug("No post-processing chain available: {}", t.toString());
            }
            probeAvailable = false;
        }
        return probeAvailable;
    }
}
