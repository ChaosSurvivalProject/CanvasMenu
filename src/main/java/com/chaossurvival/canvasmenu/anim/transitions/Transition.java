package com.chaossurvival.canvasmenu.anim.transitions;

import com.chaossurvival.canvasmenu.gui.render.Easing;

/**
 * 允许出现的动效种类。
 *
 * <p>时长集中在 150 / 240 / 320 三档，<strong>单次绝不超过 320ms</strong>；
 * 不做常驻旋转或闪烁，尊重 reduce-motion。集中定义便于统一审计。</p>
 */
public enum Transition {

    /** Screen 进入：淡入 + 0.98→1.0 缩放。 */
    ENTER(240L, Easing.EASE_OUT_CUBIC),
    /** Screen 退出：淡出。 */
    EXIT(150L, Easing.EASE_IN_OUT_SINE),
    /** 组件 hover：抬升 2px + 微光。 */
    HOVER(150L, Easing.EASE_OUT_CUBIC),
    /** Tab / Rail 下划线滑动。 */
    TAB(240L, Easing.EASE_OUT_EXPO),
    /** 卡片名称展开。 */
    CARD_EXPAND(320L, Easing.SPRING);

    /** 动效时长上限（毫秒）。 */
    public static final long MAX_DURATION_MS = 320L;

    private final long millis;
    private final Easing easing;

    Transition(long millis, Easing easing) {
        this.millis = Math.min(millis, MAX_DURATION_MS);
        this.easing = easing;
    }

    /**
     * @return 标称时长（毫秒）
     */
    public long millis() {
        return millis;
    }

    /**
     * @param reduceMotion 是否减少动效
     * @return 实际时长；减少动效时为 0
     */
    public long millis(boolean reduceMotion) {
        return reduceMotion ? 0L : millis;
    }

    /**
     * @return 缓动曲线
     */
    public Easing easing() {
        return easing;
    }
}
