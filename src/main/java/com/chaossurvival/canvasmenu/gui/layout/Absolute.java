package com.chaossurvival.canvasmenu.gui.layout;

import com.chaossurvival.canvasmenu.gui.widget.Widget;
import com.chaossurvival.canvasmenu.theme.Theme;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 绝对坐标 + 锚点布局。
 *
 * <p>每个子组件显式绑定一个 {@link Anchor} 与像素偏移；未显式绑定的组件
 * 默认落在父区域左上角并加上一层 {@code Theme.gap} 间距。</p>
 */
public final class Absolute implements Layout {

    private final Map<Widget, Placement> placements = new LinkedHashMap<>();

    /** 单个组件的定位描述。 */
    private record Placement(Anchor anchor, int offsetX, int offsetY, int gapLevel) {
    }

    /**
     * 绑定一个组件。
     *
     * @param widget   组件
     * @param anchor   锚点
     * @param offsetX  x 偏移
     * @param offsetY  y 偏移
     * @return 自身，便于链式调用
     */
    public Absolute add(Widget widget, Anchor anchor, int offsetX, int offsetY) {
        return add(widget, anchor, offsetX, offsetY, 1);
    }

    /**
     * 绑定一个组件并指定间距层级。
     *
     * @param widget   组件
     * @param anchor   锚点
     * @param offsetX  x 偏移
     * @param offsetY  y 偏移
     * @param gapLevel 间距层级
     * @return 自身
     */
    public Absolute add(Widget widget, Anchor anchor, int offsetX, int offsetY, int gapLevel) {
        placements.put(widget, new Placement(anchor, offsetX, offsetY, gapLevel));
        return this;
    }

    /** 清除全部绑定。 */
    public void clear() {
        placements.clear();
    }

    @Override
    public void layout(Region parent, List<? extends Widget> children, Theme theme) {
        for (Widget child : children) {
            Placement placement = placements.get(child);
            int pw = Math.max(1, child.preferredWidth());
            int ph = Math.max(1, child.preferredHeight());
            if (placement == null) {
                int gap = theme.gap(1);
                child.setBounds(parent.x() + gap, parent.y() + gap, pw, ph);
                continue;
            }
            int[] xy = placement.anchor().resolve(parent.x(), parent.y(),
                    parent.width(), parent.height(), pw, ph);
            int gap = theme.gap(placement.gapLevel());
            child.setBounds(xy[0] + placement.offsetX() + gap,
                    xy[1] + placement.offsetY() + gap, pw, ph);
        }
    }
}
