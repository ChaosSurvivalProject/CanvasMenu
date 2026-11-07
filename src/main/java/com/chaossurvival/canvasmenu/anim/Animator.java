package com.chaossurvival.canvasmenu.anim;

import com.chaossurvival.canvasmenu.gui.render.Easing;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 全局动画时钟。
 *
 * <p>由当前顶层 {@code CanvasScreen#tick()} 以 50ms/tick 推进，其余 Screen 不重复驱动。
 * reduce-motion 开启时所有插值一次性跳到终值，实现「尊重系统减少动效」。</p>
 */
public final class Animator {

    /** Minecraft 每个客户端 tick 的标称时长。 */
    public static final long TICK_MS = 50L;

    private static final List<Tween> ACTIVE = new CopyOnWriteArrayList<>();
    private static long clockMs = 0L;
    private static volatile boolean reduceMotion = false;

    private Animator() {
    }

    /**
     * 推进全局时钟并更新所有活跃插值。
     *
     * @param deltaMs 距上次 tick 的毫秒数
     */
    public static void tick(long deltaMs) {
        clockMs += Math.max(0L, deltaMs);
        if (ACTIVE.isEmpty()) {
            return;
        }
        long step = reduceMotion ? Long.MAX_VALUE / 4L : deltaMs;
        for (Tween tween : ACTIVE) {
            tween.advance(step);
            if (tween.isDone()) {
                ACTIVE.remove(tween);
            }
        }
    }

    /**
     * 注册一条插值（幂等）。
     *
     * @param tween 插值
     */
    public static void register(Tween tween) {
        if (!ACTIVE.contains(tween)) {
            ACTIVE.add(tween);
        }
    }

    /**
     * 反注册一条插值。
     *
     * @param tween 插值
     */
    public static void unregister(Tween tween) {
        ACTIVE.remove(tween);
    }

    /**
     * 创建并启动一条插值。
     *
     * @param from   起始值
     * @param to     终值
     * @param millis 时长
     * @param easing 缓动
     * @return 已启动的 Tween
     */
    public static Tween animate(float from, float to, long millis, Easing easing) {
        return Tween.of(from, to, millis, easing).start();
    }

    /**
     * @return 全局虚拟时钟（毫秒），供防抖计时使用
     */
    public static long now() {
        return clockMs;
    }

    /**
     * @param enabled 是否减少动效
     */
    public static void setReduceMotion(boolean enabled) {
        reduceMotion = enabled;
    }

    /**
     * @return 是否处于减少动效模式
     */
    public static boolean isReduceMotion() {
        return reduceMotion;
    }

    /**
     * @return 当前活跃插值数量（诊断用）
     */
    public static int activeCount() {
        return ACTIVE.size();
    }

    /** 清空所有活跃插值（切换 Screen 时调用）。 */
    public static void clear() {
        ACTIVE.clear();
    }
}
