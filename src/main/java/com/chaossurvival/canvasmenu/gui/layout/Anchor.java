package com.chaossurvival.canvasmenu.gui.layout;

/**
 * 九宫格锚点，用于绝对定位。
 *
 * <p>与 {@link Absolute} 配合，把子组件的某个锚点对齐到父区域的对应位置。</p>
 */
public enum Anchor {

    /** 左上。 */
    TOP_LEFT(0.0f, 0.0f),
    /** 上中。 */
    TOP_CENTER(0.5f, 0.0f),
    /** 右上。 */
    TOP_RIGHT(1.0f, 0.0f),
    /** 左中。 */
    CENTER_LEFT(0.0f, 0.5f),
    /** 正中。 */
    CENTER(0.5f, 0.5f),
    /** 右中。 */
    CENTER_RIGHT(1.0f, 0.5f),
    /** 左下。 */
    BOTTOM_LEFT(0.0f, 1.0f),
    /** 下中。 */
    BOTTOM_CENTER(0.5f, 1.0f),
    /** 右下。 */
    BOTTOM_RIGHT(1.0f, 1.0f);

    private final float factorX;
    private final float factorY;

    Anchor(float factorX, float factorY) {
        this.factorX = factorX;
        this.factorY = factorY;
    }

    /**
     * 计算子组件左上角坐标。
     *
     * @param parentX      父区域 x
     * @param parentY      父区域 y
     * @param parentWidth  父区域宽
     * @param parentHeight 父区域高
     * @param childWidth   子组件宽
     * @param childHeight  子组件高
     * @return {@code int[]{x, y}}
     */
    public int[] resolve(int parentX, int parentY, int parentWidth, int parentHeight,
                         int childWidth, int childHeight) {
        int x = parentX + Math.round((parentWidth - childWidth) * factorX);
        int y = parentY + Math.round((parentHeight - childHeight) * factorY);
        return new int[]{x, y};
    }
}
