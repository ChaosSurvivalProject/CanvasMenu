package com.chaossurvival.canvasmenu.anim;

import com.chaossurvival.canvasmenu.gui.render.Easing;

/**
 * 单次数值插值。
 *
 * <p>由 {@link Animator} 的全局虚拟时钟推进，<strong>不自行读取系统时钟</strong>：
 * 这样暂停、reduce-motion、测试都能统一控制。动画完成后对象仍保留终值，
 * 组件持有引用即可安全读取。</p>
 */
public final class Tween {

    private float from;
    private float to;
    private float value;
    private long durationMs;
    private long elapsedMs;
    private Easing easing;
    private boolean started;
    private boolean done;

    private Tween(float from, float to, long durationMs, Easing easing) {
        this.from = from;
        this.to = to;
        this.value = from;
        this.durationMs = Math.max(0L, durationMs);
        this.easing = easing == null ? Easing.EASE_OUT_CUBIC : easing;
    }

    /**
     * 创建一条插值。
     *
     * @param from       起始值
     * @param to         终值
     * @param durationMs 时长（毫秒），≤0 表示立即完成
     * @param easing     缓动曲线
     * @return Tween 实例（尚未注册到 Animator）
     */
    public static Tween of(float from, float to, long durationMs, Easing easing) {
        return new Tween(from, to, durationMs, easing);
    }

    /**
     * 创建一条使用默认缓出的插值。
     *
     * @param from       起始值
     * @param to         终值
     * @param durationMs 时长（毫秒）
     * @return Tween 实例
     */
    public static Tween of(float from, float to, long durationMs) {
        return new Tween(from, to, durationMs, Easing.EASE_OUT_CUBIC);
    }

    /**
     * 注册到全局 Animator 并开始推进。
     *
     * @return 自身，便于链式调用
     */
    public Tween start() {
        started = true;
        if (durationMs <= 0L) {
            value = to;
            done = true;
        }
        Animator.register(this);
        return this;
    }

    /**
     * 改变目标值并重新开始（用于 hover 中途反向、tab 中途切换）。
     *
     * @param newTo      新终值
     * @param millis     新时长
     * @param newEasing  新缓动
     * @return 自身
     */
    public Tween retarget(float newTo, long millis, Easing newEasing) {
        this.from = value;
        this.to = newTo;
        this.durationMs = Math.max(0L, millis);
        this.easing = newEasing == null ? Easing.EASE_OUT_CUBIC : newEasing;
        this.elapsedMs = 0L;
        this.done = false;
        this.started = true;
        if (this.durationMs <= 0L) {
            this.value = newTo;
            this.done = true;
        }
        Animator.register(this);
        return this;
    }

    /** 由 Animator 调用，推进插值。 */
    void advance(long deltaMs) {
        if (!started || done) {
            return;
        }
        if (durationMs <= 0L) {
            value = to;
            done = true;
            return;
        }
        elapsedMs += Math.max(0L, deltaMs);
        float t = Math.min(1.0f, (float) elapsedMs / (float) durationMs);
        value = from + (to - from) * easing.clamped(t);
        if (t >= 1.0f) {
            value = to;
            done = true;
        }
    }

    /**
     * @return 当前插值
     */
    public float get() {
        return value;
    }

    /**
     * 直接设定当前值（不启动动画）。
     *
     * @param v 新值
     */
    public void set(float v) {
        this.value = v;
    }

    /**
     * @return 是否已完成
     */
    public boolean isDone() {
        return done;
    }

    /**
     * @return 时长（毫秒）
     */
    public long durationMs() {
        return durationMs;
    }

    /** 复位到起始状态。 */
    public void reset() {
        this.value = from;
        this.elapsedMs = 0L;
        this.done = false;
        this.started = false;
    }
}
