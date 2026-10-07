package com.z80z99.z80zhealthbar.util;

/**
 * 氧气气泡行的计数（原版 {@code ForgeGui.renderAir} 逐指令核对的纯函数版,便于单测）。
 *
 * <p>语义（以原版 300 点/10 格为例）：
 * <ul>
 *   <li><b>满泡</b> = {@code ceil((air - 2) * slots / max)}——预留 2 点,让边界气泡"正在消耗"
 *       而不是一掉气就破裂；</li>
 *   <li><b>正在破裂的边界泡</b> = {@code ceil(air * slots / max) - 满泡}（只有 0 或 1 个）；</li>
 *   <li>其余槽位<b>不绘制</b>——气泡破掉即消失,氧气为 0 时整行为空。</li>
 * </ul>
 *
 * <p>此前的实现把"将破"贴图铺满全部槽位当槽底,于是破掉的气泡看起来永久残留、
 * 氧气耗尽时仍占一整行（用户实测反馈）。
 */
public final class AirBubbleRow {

    private AirBubbleRow() {}

    /**
     * 氧气行是否显示——**原版对玩家的规则**（{@code ForgeGui.renderAir}：
     * {@code isEyeInFluidType(WATER) || air < maxAir}），外加"耗尽即隐藏"（原版 0 氧气不画任何气泡）。
     *
     * <p>关键点：只按"空气未满"判会漏掉两类情形——亡灵/水生生物在水下不掉氧（空气恒满），
     * 以及出水回氧中（空气未满但已离水）。两者都应按"眼睛在水里"判定。
     */
    public static boolean rowVisible(boolean eyeInWater, int air, int maxAir) {
        return (eyeInWater || air < Math.max(1, maxAir)) && air > 0;
    }

    /**
     * @param air   当前氧气
     * @param max   氧气上限（≤0 视为 1）
     * @param slots 槽位数（气泡格数,通常 10）
     * @return {@code [满泡数, 破裂泡数]}，两者之和 = 应绘制的气泡数（0 = 整行不绘制）
     */
    public static int[] counts(int air, int max, int slots) {
        int m = Math.max(1, max);
        int s = Math.max(0, slots);
        int a = Math.max(0, Math.min(air, m));
        int full = (int) Math.ceil((a - 2) * (double) s / m);
        int total = (int) Math.ceil(a * (double) s / m);
        full = Math.max(0, Math.min(s, full));
        int popping = Math.max(0, Math.min(s - full, total - full));
        return new int[]{full, popping};
    }
}
