package com.chaossurvival.canvasmenu.gui.layout;

import com.chaossurvival.canvasmenu.gui.widget.Widget;
import com.chaossurvival.canvasmenu.theme.Theme;

import java.util.List;

/**
 * 布局策略根接口。
 *
 * <p>窗口尺寸变化时由 {@code CanvasScreen} 重新调用 {@link #layout}，
 * 所有间距取自 {@link Theme#gap(int)}，禁止硬编码。</p>
 */
public interface Layout {

    /**
     * 在父区域内排列子组件。
     *
     * @param parent   父区域
     * @param children 子组件（调用方已按期望顺序排好）
     * @param theme    当前主题
     */
    void layout(Region parent, List<? extends Widget> children, Theme theme);
}
