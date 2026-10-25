package com.z80z99.z80zhealthbar.config.configs;

/**
 * 自定义聊天窗口（1.0.150）：像独立窗口一样的聊天记录 + 可分离输入框。
 * 默认关闭（opt-in）——关闭时原版聊天零改动。数据仍走原版 ChatComponent 存储。
 */
public class ChatWindowConfig {
    /** 总开关：接管原版聊天渲染与聊天输入界面 */
    public boolean enabled = false;
    /** 窗口位置（GUI 像素；-1 = 默认左下） */
    public int x = -1;
    public int y = -1;
    /** 窗口宽（120..800,内容宽=宽-6） */
    public int width = 280;
    /** 窗口高（60..600） */
    public int height = 160;
    /** 窗口整体缩放（0.5..2,绕左上角） */
    public double scale = 1.0;
    /** 自动换行（关 = 超宽截断省略号） */
    public boolean wordWrap = true;
    /** 背景不透明度（0..100,默认 40≈原版 0x66） */
    public int bgOpacity = 40;
    /** 静默 N 秒后淡出（0 = 常驻显示;动态 HUD 同语义） */
    public int idleFadeSecs = 0;
    /** 输入框吸附窗口下沿（false = 独立位置 inputX/inputY） */
    public boolean inputAttached = true;
    /** 输入框独立位置（-1 = 屏幕底部居中兜底） */
    public int inputX = -1;
    public int inputY = -1;
    /** 动画总开关（弹出/插入/平滑） */
    public boolean animEnabled = true;
    /** 平滑滚动 */
    public boolean smoothScroll = true;
}
