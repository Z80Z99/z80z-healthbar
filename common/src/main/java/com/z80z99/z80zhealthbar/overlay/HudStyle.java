package com.z80z99.z80zhealthbar.overlay;

/** 玩家 HUD 样式（任务书 4.2）：三模式与总开关 enableOverlay 相互独立。 */
public enum HudStyle {
    /** 原版 HUD：本 MOD 不接管、不屏蔽、不重复绘制任何原版元素 */
    VANILLA,
    /** AsteorBar 长条系统：9 种布局样式（overlayLayoutStyle 0-8，0=关闭本模式绘制） */
    ASTEORBAR,
    /** 自定义：组件级样式/锚点/偏移/缩放（HudLayoutConfig + HudLayoutSolver + 布局编辑器） */
    CUSTOM
}
