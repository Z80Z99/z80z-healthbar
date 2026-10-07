package com.z80z99.z80zhealthbar.config.configs;

import java.util.LinkedHashMap;
import java.util.Map;

public class OverlayConfig {
    public boolean enableOverlay = true;
    /** 玩家 HUD 样式（HudStyle 名称字符串，防御性解析） */
    public String hudStyle = "ASTEORBAR";
    public int overlayLayoutStyle = 2;
    public double overlayTextScale = 1.0;
    public int overlayBarInnerHeight = 5;
    public int overlayBarVerticalMargin = 2;
    public int overlayBarTextOffsetY = 0;
    /** 0 = 跟随实际最大值（默认；写死正值会覆盖实际上限，如血量增强模组下血条恒满） */
    public int fullFoodLevelValue = 0;
    public double fullSaturationValue = 20.0;
    public int fullArmorValue = 0;
    public int fullHealthValue = 0;
    public int hideUnchangingBarAfterSeconds = 0;
    public int absorptionMode = 0;
    public boolean enableHealthBlink = true;
    public double lowHealthRate = 0.2;
    public boolean shakeHealthAndFoodWhileLow = true;
    public boolean overwriteVanillaArmorBar = true;
    public boolean overwriteVanillaExperienceBar = true;
    public boolean displayExperienceProgress = false; // Wave 12 修复：默认不显示百分比
    public boolean displayExperienceLevel = true;
    public boolean displayHealthText = true;
    public boolean enableFoodBlink = true;
    public boolean displaySaturation = true;
    public boolean displayFoodText = true;
    public boolean displayArmorToughness = true;
    public int cornerBarLength = 100;
    public int cornerHorizontalPadding = 5;
    public int cornerVerticalPadding = 5;
    public boolean mountHealthOnLeftSide = false;

    /** 长条样式逐条自由摆放（HUD 布局编辑器拖拽写回）：free=true 的条脱离预设布局
     *  （overlayLayoutStyle 0-8 + 边距堆叠）按绝对坐标渲染；未拖过的条不在此表 = 跟随预设。
     *  visible/showIcon/iconOffX/iconOffY/showText/textOffX/textOffY 为组件级配置：
     *  条本体显示开关、状态图标开关与独立偏移（图标与条拆开）、数值文本开关与独立偏移（文本与条拆开）。 */
    public Map<String, BarFreePos> barFreePos = new LinkedHashMap<>();

    public static class BarFreePos {
        public boolean free;
        public int x, y;
        public boolean visible = true;
        public boolean showIcon = true;
        public int iconOffX, iconOffY;
        public boolean showText = true;
        public int textOffX, textOffY;
        /** 饱和度显示方式（仅饱食度条）：0=覆盖 1=右侧追加(吸收式) 2=顶部细条 3=底部细条 4=关闭 */
        public int saturationMode = 0;
    }
}
