package com.z80z99.z80zhealthbar.config.configs;

/**
 * 实体血条动态效果（样式 A/B/C 通用）。
 *
 * <ul>
 *   <li>smooth：血量变化时填充平滑过渡（指数逼近），不逐帧跳变；</li>
 *   <li>ghost：伤害残影——掉血后在原血量位置留下一段慢速收缩的苍白残影；</li>
 *   <li>hurtFlash：实体受伤瞬间（hurtTime）填充闪白。</li>
 * </ul>
 * 心形行/牌匾为离散图标，不适用平滑与残影。
 */
public class DynamicFxConfig {
    public boolean enabled = true;
    public boolean smooth = true;
    public boolean ghost = true;
    public boolean hurtFlash = true;
    /** 残影颜色（ARGB），默认半透明白 */
    public String ghostColor = "#80FFFFFF";
    /** 数字动态效果：血量数值平滑滚动到新值（ARPG 计数器风格） */
    public boolean numRoll = true;
    /** 数字动态效果：受伤瞬间数值弹跳放大 */
    public boolean numPunch = true;
    /** 数字动态效果：受伤闪红、治疗闪绿 */
    public boolean numTint = true;
}
