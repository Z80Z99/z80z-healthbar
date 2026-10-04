package com.z80z99.z80zhealthbar.config.configs;

public class PlaqueStyleConfig {
    public boolean enablePlaques = true;
    public double plaqueScale = 0.5;
    public int maxPlaqueRowWidth = 108;
    public boolean scaleWithDistance = true;
    public boolean renderBelowNameTag = false;
    public boolean plaqueBackground = true;
    public boolean behindWalls = true;
    public int maxRenderDistance = 48;
    public int heightOffset = 0;
    /** 牌匾水平偏移（牌匾像素；正值=屏幕向右） */
    public int xOffset = 0;
    /** 牌匾垂直偏移（牌匾像素；正值=屏幕向下） */
    public int yOffset = 0;
    /** 牌匾整体不透明度（0-255；图标/背景/数值统一淡出） */
    public int opacity = 255;
    /** 数值文字缩放（1.0=默认）；行宽按缩放重算以避免溢出背景 */
    public double textScale = 1.0;
    /** 数值文字水平/垂直偏移（牌匾像素；正值向右/向下） */
    public int textOffsetX = 0;
    public int textOffsetY = 0;
    /** 样式 B 各牌匾行的独立开关（对应原 MOD 每类 plaque 的 allow_rendering） */
    public boolean showArmorRow = true;
    public boolean showToughnessRow = true;
    public boolean showAirRow = true;
    /** 调试：按反编译源逐行复刻 Mob Plaques 牌匾渲染（文字左+单图标右、原版配色） */
    public boolean originalRender = false;

    public enum FullBrightnessMode {
        NEVER, UNOBSTRUCTED, ALWAYS
    }
    public FullBrightnessMode fullBrightness = FullBrightnessMode.UNOBSTRUCTED;
}
