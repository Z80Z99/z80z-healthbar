package com.z80z99.z80zhealthbar.overlay;

import java.util.Map;

/**
 * 血条动态效果的第二层纯函数（状态层仍是 BarFx：display/ghost/flash/heal）。
 * 本类只做"时间 → 视觉参数"计算，全部可纯 JVM 单测：
 * <ul>
 *   <li>{@link #pulse(long)} 低血呼吸明暗系数</li>
 *   <li>{@link #sheenBand(int, int, long, int)} 扫光带区间（裁剪在填充内）</li>
 *   <li>{@link #shakeOffset(long, float)} 受击抖动 y 偏移</li>
 *   <li>{@link #fadeAlpha(long, long, boolean)} 出现/消失淡入淡出透明度</li>
 *   <li>{@link #textTint(float, float)} 数字受伤红/治疗绿颜色混合系数</li>
 * </ul>
 */
public final class HudFx {

    /** 低血呼吸周期（ms/rad）：190 → 约 1.2s 一个完整呼吸（更快更醒目） */
    public static final double PULSE_PERIOD_MS = 190.0;
    /** 扫光完整周期（ms）——仅作旧文档参考,速度模型已改为恒定像素速度（见 SHEEN_SPEED_PX_PER_SEC） */
    public static final long SHEEN_PERIOD_MS = 2600;
    /**
     * 扫光基准像素速度（px/s）——**恒定速度**设计:任何条宽/填充长度下像素速度一致
     * （周期 = 距离/速度,条越长扫越久）。旧实现固定 2.6s 周期,距离=填充宽+带宽,
     * 满血条比残血条快近 5 倍（实测反馈"速度不均匀"）。
     */
    public static final double SHEEN_SPEED_PX_PER_SEC = 56.0;
    /** 自适应倍率（仅动态效果.sheenAdaptive 开启时生效:数值变化中短暂加速） */
    private static final double SHEEN_ADAPTIVE_BOOST = 1.5;
    /** 抖动序列（与 SimpleBarOverlay.SHIFT 同款 0/1 伪随机,独立副本避免跨类耦合） */
    private static final int[] SHAKE = {0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1};

    private HudFx() {}

    /** 低血脉冲系数 0..1（呼吸曲线,渲染端 fill = lerp(fill, 提亮, p·0.35)） */
    public static float pulse(long now) {
        return (float) (0.5 + 0.5 * Math.sin(now / PULSE_PERIOD_MS));
    }

    /**
     * 扫光带的可见区间（光带起点 = {@link #advanceSheen} 维护的当前位置）。
     *
     * <p>2026-10-08 二次修正:位置**直接累加**、与扫一趟的距离（travel = fillW + bandW）解耦——
     * 旧实现 pos = phase mod travel,填充宽变化时取模回绕把"填充变化速度 × 已完成圈数"乘进
     * 光带速度（实测复现:60s 运行时掉血动画期间 ±3000~4400px/s,基准仅 56px/s）,血量变化时观感"飞快"。
     *
     * @param innerW 条内宽（光晕带宽 = max(10, innerW/5)）
     * @param fillW 当前填充宽（扫光只在有血区域流动）
     * @param posPx 光带起点相对填充左缘的位置（px,合法域 [-bandW, fillW)）
     * @return {start, end} 裁剪到填充内的像素区间;不可见（如 fillW=0 或光带整体出条）返回 null
     */
    public static int[] sheenBandPhase(int innerW, int fillW, double posPx) {
        if (fillW <= 0) return null;
        int bandW = Math.max(10, innerW / 5);
        int pos = (int) Math.round(posPx);
        int start = Math.max(0, pos);
        int end = Math.min(fillW, pos + bandW);
        if (end - start <= 0) return null;
        return new int[]{start, end};
    }

    /** 旧签名兼容（纯时间相位→位置;仅历史/测试调用,正式路径一律经 advanceSheen） */
    public static int[] sheenBand(int innerW, int fillW, long now, int phase) {
        if (fillW <= 0) return null;
        int bandW = Math.max(10, innerW / 5);
        long travel = fillW + bandW;
        double pos = -bandW + Math.floorMod((long) ((now + phase) * SHEEN_SPEED_PX_PER_SEC / 1000.0), travel);
        return sheenBandPhase(innerW, fillW, pos);
    }

    // ---- 扫光位置推进（恒定像素速度;可选自适应加速） ----
    // int key（实体 ID / 固定组件编号）——此前 String key 每帧拼接（"mob."+id）且表无界增长
    private static final Map<Integer, double[]> SHEEN = new java.util.HashMap<>(); // {posPx, lastMs, lastFill}
    private static final int SHEEN_STALE_MS = 10_000;

    /**
     * 扫光位置推进（**恒定像素速度**,dt 以真实时间计,与帧率无关）。方向恒为右行。
     *
     * <p>位置与 travel 解耦:填充宽怎样变化（掉血/回血动画）都不影响光带速度;
     * 出右缘（pos ≥ fillW,整体不可见）即回绕到左外,周期 = travel/速度
     * （随条长自适应,像素速度全局一致）。
     *
     * @param key     位置跟踪键（实体 ID;玩家组件用负编号:生命 -1 / 自定义 -2 / 经验 -3）
     * @param innerW  条内宽（决定光带宽 = max(10, innerW/5),回绕起点用）
     * @param fillW   当前填充宽（变化检测;供自适应加速判定）
     * @param now     时间戳
     * @param adaptive 是否启用自适应加速（dynamicFx.sheenAdaptive;变化中 ×1.5,静止恢复基准）
     * @return 光带起点位置（px,相对填充左缘）——直接喂 {@link #sheenBandPhase}
     */
    public static double advanceSheen(int key, int innerW, int fillW, long now, boolean adaptive) {
        int bandW = Math.max(10, innerW / 5);
        double[] st = SHEEN.get(key);
        if (st == null) {
            if (SHEEN.size() > 256) { // 有界清理:整体重建（存活条目 << 256,代价可忽略）
                SHEEN.entrySet().removeIf(e -> now - e.getValue()[1] > SHEEN_STALE_MS);
            }
            st = new double[]{-bandW, now, fillW};
            SHEEN.put(key, st);
            return st[0];
        }
        long dt = Math.max(0, Math.min(100, now - (long) st[1]));
        double prevFill = st[2];
        boolean grew = fillW > prevFill + 0.5;
        boolean shrank = fillW < prevFill - 0.5;
        double speed = SHEEN_SPEED_PX_PER_SEC * (adaptive && (grew || shrank) ? SHEEN_ADAPTIVE_BOOST : 1.0);
        st[0] += dt / 1000.0 * speed;
        double over = st[0] - fillW; // 越界量（>=0 即出右缘）
        if (over >= 0) {
            // 回绕至左外;保留越界量（限速内）使速度严格连续,填充骤缩时越界量大也不许弹回条内
            st[0] = -bandW + Math.min(over, speed * dt / 1000.0);
        }
        st[1] = now;
        st[2] = fillW;
        return st[0];
    }

    /**
     * 扫光单条带的白色 alpha（0..0x66）——渐变分布,非平铺方块：
     * <ul>
     *   <li>主体光晕：对称余弦窗（中段最亮、两端渐隐至 0）;峰值 0x2A;</li>
     *   <li>前缘亮点：位于 t≈3/4（右行方向的前部）,窄而亮;峰值 0x55;</li>
     *   <li>两者叠加后整体钳制 0x66（≈40% 白,清晰但不刺眼）。</li>
     * </ul>
     *
     * @param relStart 该切片相对光带左缘的起点（px）
     * @param sliceW   切片宽（px）
     * @param bandW    光带总宽（px）
     */
    public static int sheenStripAlpha(int relStart, int sliceW, int bandW) {
        if (bandW <= 0 || sliceW <= 0) return 0;
        float t = (relStart + sliceW * 0.5f) / bandW;
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        float halo = (float) Math.sin(Math.PI * t);
        halo *= halo;
        float g = 1f - Math.abs(t - 0.75f) / 0.25f;
        if (g < 0f) g = 0f;
        g *= g;
        int a = Math.round(halo * 0x2A) + Math.round(g * 0x55);
        return Math.min(0x66, a);
    }

    /** 扫光切片绘制回调（各管线的 fill 原语签名不同——GuiGraphics.fill / VertexConsumer 矩形） */
    public interface SheenStrip {
        void fill(int x, int w, int alpha);
    }

    /**
     * 把扫光带按亮度切片交给渲染端绘制（x 相对填充左缘;alpha 为白色通道,未乘管线透明度）。
     * 切片步进 ≤ bandW/16,15-30px 的光带约 8-16 次 fill——渐变肉眼连续,开销可忽略。
     */
    public static void drawSheen(int[] band, SheenStrip sink) {
        if (band == null) return;
        int bandW = band[1] - band[0];
        int step = Math.max(1, bandW / 16);
        for (int x = band[0]; x < band[1]; x += step) {
            int w = Math.min(band[1], x + step) - x;
            int a = sheenStripAlpha(x - band[0], w, bandW);
            if (a > 0) sink.fill(x, w, a);
        }
    }

    /** 抖动方式：0=关 1=静态(0/1 序列,原版) 2=平滑比例(正弦,幅度∝强度) 3=动态(幅度∝伤害量) */
    public static final int SHAKE_OFF = 0, SHAKE_STATIC = 1, SHAKE_SMOOTH = 2, SHAKE_DYNAMIC = 3;

    /** 受击抖动 y 偏移（平滑正弦版,取代 0/1 跳变——实测反馈"抖动更平滑"） */
    public static int shakeSmooth(long now, float intensity, float ampPx) {
        if (intensity <= 0f) return 0;
        float osc = (float) Math.sin(now / 40.0); // 40ms 半周期,肉眼平滑
        return Math.round(osc * Math.min(1f, intensity) * ampPx);
    }

    /** 静态抖动（原版 0/1 序列,恒幅 2px） */
    public static int shakeStatic(long now, float intensity) {
        if (intensity <= 0f) return 0;
        int idx = (int) ((now / 50) % SHAKE.length);
        return Math.round(SHAKE[idx] * Math.min(1f, intensity) * 2);
    }

    /** 按抖动方式分派：1 静态（恒幅 0/1 序列）/ 2 平滑比例（正弦,幅度∝受击强度,默认）/
     *  3 动态（幅度∝本次伤害量:小伤害轻抖、大伤害猛抖）/ 其它=关 */
    public static int shakeByMode(int mode, long now, float flash, float lastDamage) {
        if (flash <= 0f) return 0;
        return switch (mode) {
            case SHAKE_STATIC -> shakeStatic(now, flash);
            case SHAKE_SMOOTH -> shakeSmooth(now, flash, 2f);
            case SHAKE_DYNAMIC -> shakeSmooth(now, flash, 1.5f + Math.min(1f, lastDamage * 4f) * 3.5f);
            default -> 0;
        };
    }

    /**
     * 淡入淡出透明度 0..1。
     *
     * @param now        当前时间
     * @param lastChange 可见性最近一次翻转的时间戳
     * @param visible    当前是否应有值
     * @return visible=true：入场 inMs 内 0→1;false：保持 holdMs 后 outMs 内 1→0
     */
    public static float fadeAlpha(long now, long lastChange, boolean visible, long inMs, long holdMs, long outMs) {
        long dt = now - lastChange;
        if (visible) {
            return clamp01(dt / (float) inMs);
        }
        if (dt < holdMs) return 1f;
        return clamp01(1f - (dt - holdMs) / (float) outMs);
    }

    /** 数字变色系数：{红, 绿}——受伤 flash 越大红越深,治疗 heal 越大绿越深 */
    public static float[] textTint(float flash, float heal) {
        return new float[]{clamp01(flash * 0.8f), clamp01(heal * 0.6f)};
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }

    /** easeOutBack（弹性过冲缓动）：t=0→0,t=1→1,中途超过 1（峰值约 1.10）后回落——弹入动画用 */
    public static float easeOutBack(float t) {
        float x = clamp01(t);
        final float c1 = 1.70158f;
        final float c3 = x - 1f;
        return 1f + (c1 + 1f) * c3 * c3 * c3 + c1 * c3 * c3;
    }

    /**
     * 弹入动画参数（实体血条首次出现）。
     *
     * @param elapsed 出现后经过时间
     * @return {scale, yOff}：scale 从 0.6 弹到 1（带过冲）,yOff 从 -6px 落到 0
     */
    public static float[] popIn(long elapsed) {
        float t = clamp01(elapsed / 220f);
        float scale = 0.6f + 0.4f * easeOutBack(t);
        float yOff = -6f * (1f - t);
        return new float[]{scale, yOff};
    }
}
