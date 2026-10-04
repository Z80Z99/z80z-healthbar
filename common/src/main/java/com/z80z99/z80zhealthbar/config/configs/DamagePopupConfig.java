package com.z80z99.z80zhealthbar.config.configs;

import java.util.Locale;

/**
 * 伤害跳字配置。主题预设参考主流 FPS 的命中反馈设计：
 * APEX=Apex Legends（护盾/生命分色 + 击杀确认）、TACTICAL=COD/守望（单色白 + 大额金色放大 + 散布）、
 * WARFRAME=Warframe（按伤害类型配色大字 + 倾斜）、CLASSIC=经典类型配色、MINIMAL=CS 竞技极简。
 * 颜色均为 #RRGGBBAA。
 */
public class DamagePopupConfig {
    /** 总开关（独立于实体血条样式与 barStyle.enableHealthBar） */
    public boolean enabled = true;
    public String theme = "APEX";
    /** 跳字运动方式:RISE=上升漂浮 ARC=弹出坠落 STACK=塔式堆叠(顶旧底新) CUMULATIVE=伤害总和 */
    public String motion = "RISE";
    /** RISE/ARC 从头顶跳出的随机偏角上限（度,相对正上方,0=垂直向上,全锥角=2×该值） */
    public double launchAngleDegrees = 60.0;
    /** 无服务端模组时用客户端血量差值估算（吸收抵消部分会偏小） */
    public boolean estimateWithoutServer = true;
    public double scale = 1.0;
    /** 头顶抬升（方块，0 ≈ 头部位置） */
    public double offsetY = 0.0;
    public int lifetimeTicks = 20;
    /** 同实体同屏堆叠上限（超出挤掉最老） */
    public int maxPerEntity = 5;
    /** N tick 内多段伤害合并为一条（求和 + ×n），0 = 关闭 */
    public int mergeWindowTicks = 0;

    /** 按伤害类型显示的开关 */
    public boolean showPhysical = true;
    public boolean showProjectile = true;
    public boolean showFire = true;
    public boolean showExplosion = true;
    public boolean showMagic = true;
    public boolean showFall = true;

    /** 准星命中标记（FPS hitmarker）与音效 */
    public boolean hitMarker = true;
    public boolean hitMarkerSound = true;

    // 配色语义分层(参考 COD 暴击金 / Apex 护盾蓝):
    //   普通白 → 大额金(≥20%生命) → 击杀绯红;吸收=护盾蓝;类型色各占独立色相
    public String colorHealth = "#FFFFFFFF";     // 普通伤害:纯白(最高可读性)
    public String colorBigHit = "#FFFFD24A";     // 大额伤害:暴击金(TACTICAL 专用,区别于击杀红)
    public String colorKill = "#FFFF1744";       // 击杀:绯红(与爆炸橙红拉开色相)
    public String colorAbsorbed = "#FF4FC8FF";   // 吸收抵消:护盾蓝(Apex 语义,MC 吸收心为金—此处蓝以避开大额金)
    public String colorFire = "#FFFFAB40";       // 火焰:亮琥珀
    public String colorExplosion = "#FFFF5722";  // 爆炸:朱砂(深橙红,与火焰/击杀均区分)
    public String colorMagic = "#FFB388FF";      // 魔法/冰冻:堇紫
    public String colorProjectile = "#FF7FB8E8"; // 投射物:钢青蓝
    public String colorFall = "#FFC8A678";       // 摔落:土棕

    /** 解析主题（非法值回退 APEX） */
    public String themeParsed() {
        String t = theme == null ? "" : theme.toUpperCase(Locale.ROOT);
        return switch (t) {
            case "APEX", "TACTICAL", "WARFRAME", "CLASSIC", "MINIMAL" -> t;
            default -> "APEX";
        };
    }

    /** 解析运动方式（非法值回退 RISE） */
    public String motionParsed() {
        String m = motion == null ? "" : motion.toUpperCase(Locale.ROOT);
        return switch (m) {
            case "RISE", "ARC", "STACK", "CUMULATIVE" -> m;
            default -> "RISE";
        };
    }
}
