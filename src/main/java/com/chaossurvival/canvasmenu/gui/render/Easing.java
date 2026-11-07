package com.chaossurvival.canvasmenu.gui.render;

/**
 * 缓动曲线集合。
 *
 * <p>统一曲线 {@code cubic-bezier(0.22,1,0.36,1)}，本枚举用解析近似实现，
 * 不引入任何外部数学库。输入自动夹取到 {@code [0,1]}，输出亦收敛到 {@code [0,1]}。</p>
 */
public enum Easing {

    /** 线性，仅用于测试与 reduce-motion 降级。 */
    LINEAR {
        @Override
        public float apply(float t) {
            return t;
        }
    },

    /** 三次缓出，进入 / hover 的主要曲线。 */
    EASE_OUT_CUBIC {
        @Override
        public float apply(float t) {
            float inv = 1.0f - t;
            return 1.0f - inv * inv * inv;
        }
    },

    /** 指数缓出，tab 下划线滑动。 */
    EASE_OUT_EXPO {
        @Override
        public float apply(float t) {
            if (t >= 1.0f) {
                return 1.0f;
            }
            return (float) (1.0d - Math.pow(2.0d, -10.0d * t));
        }
    },

    /** 正弦缓入缓出，退出动效。 */
    EASE_IN_OUT_SINE {
        @Override
        public float apply(float t) {
            return (float) (-(Math.cos(Math.PI * t) - 1.0d) / 2.0d);
        }
    },

    /** 轻弹簧，卡片展开；终点强制归 1 避免残留抖动。 */
    SPRING {
        @Override
        public float apply(float t) {
            if (t >= 1.0f) {
                return 1.0f;
            }
            double value = 1.0d - Math.exp(-6.0d * t) * Math.cos(9.0d * t);
            return (float) Math.max(0.0d, Math.min(1.0d, value));
        }
    };

    /**
     * 应用缓动。
     *
     * @param t 归一化进度
     * @return 缓动后的进度
     */
    public abstract float apply(float t);

    /**
     * 夹取输入后应用缓动。
     *
     * @param t 任意进度值
     * @return 缓动后的进度
     */
    public final float clamped(float t) {
        return apply(Math.max(0.0f, Math.min(1.0f, t)));
    }

    /**
     * 按名称解析缓动，未知名称回退三次缓出。
     *
     * @param name 名称（忽略大小写）
     * @return 缓动实例
     */
    public static Easing fromName(String name) {
        if (name == null) {
            return EASE_OUT_CUBIC;
        }
        for (Easing e : values()) {
            if (e.name().equalsIgnoreCase(name.trim())) {
                return e;
            }
        }
        return EASE_OUT_CUBIC;
    }
}
