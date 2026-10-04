package com.z80z99.z80zhealthbar.config.configs;

public class OverlayConfig {
    public boolean enableOverlay = true;
    /** 玩家 HUD 样式（HudStyle 名称字符串，防御性解析） */
    public String hudStyle = "ASTEORBAR";
    public int overlayLayoutStyle = 2;
    public double overlayTextScale = 1.0;
    public int overlayBarInnerHeight = 5;
    public int overlayBarVerticalMargin = 2;
    public int overlayBarTextOffsetY = 0;
    public double hideDecimalWhenEqualOrMoreThan = 100.0;
    /** 0 = 跟随实际最大值（默认；写死正值会覆盖实际上限，如血量增强模组下血条恒满） */
    public int fullFoodLevelValue = 0;
    public double fullSaturationValue = 20.0;
    public double fullExhaustionValue = 4.0;
    public int fullArmorValue = 0;
    public int fullArmorToughnessValue = 20;
    public int fullHealthValue = 0;
    public boolean enableStackHealthBar = false;
    public String stackHealthBarColors = "#FFFFFF,#00FF00,#FF0000";
    public double healthRegenerationOpacity = 0.3;
    public double healthRegenerationOpacityOnFull = 0.0;
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
    public boolean displayAbsorptionDivMaxHealth = false;
    public boolean enableFoodBlink = true;
    public boolean displaySaturation = true;
    public boolean displayExhaustion = true;
    public boolean displayFoodText = true;
    public boolean displayArmorToughness = true;
    public int cornerBarLength = 100;
    public int cornerHorizontalPadding = 5;
    public int cornerVerticalPadding = 5;
    public boolean forceRenderAtCorner = false;
    public boolean mountHealthOnLeftSide = false;
}
