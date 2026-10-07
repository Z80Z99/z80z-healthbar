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
    /** 低血脉冲：低于阈值时填充颜色呼吸明暗（玩家条阈值 = overlay.lowHealthRate,实体条固定 0.3） */
    public boolean lowHpPulse = true;
    /** 扫光流动：填充区周期性扫过一道移动高光带 */
    public boolean sheen = true;
    /** 治疗泛光：回血时填充短暂泛绿（与 numTint 的数字变绿互补,本效果作用于填充本体） */
    public boolean healGlow = true;
    /** 受击抖动：受伤瞬间条本体垂直抖动,幅度随受击闪白衰减收敛 */
    public boolean hitShake = true;
    /** 抖动方式：0=关 1=静态(恒幅 0/1 序列) 2=平滑比例(正弦,幅度∝受击强度,默认) 3=动态(幅度∝本次伤害量) */
    public int shakeMode = 2;
    /** 扫光动态速率：数值变化时 1.0 倍速、静止 0.5 倍速,方向随数据增减 */
    public boolean sheenAdaptive = true;
    /** 实体条弹入：血条首次出现时缩放弹入 + 从上方落入（220ms,替代纯透明度淡入的呆板出场） */
    public boolean spawnPop = true;
    /** 实体条治疗上浮：回血脉冲期血条整体上浮并随治疗衰减回落 */
    public boolean healLift = true;
    /** 实体条死亡收缩：死亡动画期血条向挂点收缩至 70% 并下沉,与死亡渐隐叠加 */
    public boolean deathShrink = true;
    /** 实体条死亡碎裂：死亡瞬间血条像玻璃一样碎成碎片飞散（开启时优先于渐隐/收缩,样式1 条形与样式3 支持;心排/牌匾回退渐隐） */
    public boolean shatter = true;
}
