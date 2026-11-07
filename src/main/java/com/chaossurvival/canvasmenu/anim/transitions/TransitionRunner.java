package com.chaossurvival.canvasmenu.anim.transitions;

import com.chaossurvival.canvasmenu.anim.Animator;
import com.chaossurvival.canvasmenu.anim.Tween;

/**
 * 把 {@link Transition} 应用到 {@link Tween} 的薄封装。
 *
 * <p>统一处理 reduce-motion（时长归零）与反向 retarget，避免每个组件各写一套。</p>
 */
public final class TransitionRunner {

    private TransitionRunner() {
    }

    /**
     * 创建并启动一条动效插值。
     *
     * @param type 动效种类
     * @param from 起始值
     * @param to   终值
     * @return 已启动的 Tween
     */
    public static Tween play(Transition type, float from, float to) {
        return Tween.of(from, to, type.millis(Animator.isReduceMotion()), type.easing()).start();
    }

    /**
     * 让已有插值以指定动效重定向到新目标值。
     *
     * @param tween 已有插值
     * @param type  动效种类
     * @param to    新终值
     * @return 同一实例
     */
    public static Tween retarget(Tween tween, Transition type, float to) {
        return tween.retarget(to, type.millis(Animator.isReduceMotion()), type.easing());
    }

    /**
     * 创建一条起始即完成、值为 {@code value} 的静态插值。
     *
     * @param value 固定值
     * @return Tween
     */
    public static Tween constant(float value) {
        Tween t = Tween.of(value, value, 0L, typeEasing());
        t.set(value);
        return t;
    }

    private static com.chaossurvival.canvasmenu.gui.render.Easing typeEasing() {
        return com.chaossurvival.canvasmenu.gui.render.Easing.LINEAR;
    }
}
