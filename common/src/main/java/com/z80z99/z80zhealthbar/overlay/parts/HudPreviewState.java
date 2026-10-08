package com.z80z99.z80zhealthbar.overlay.parts;

/**
 * 玩家 HUD 预览的模拟战斗状态（仅设置页预览激活,真实 HUD 渲染不走这里）：
 * 6 秒循环——5 次掉血至 3 HP（演示低血量闪烁/抖动与填充平滑动画）+ 循环末回血;
 * 饥饿随命中下降（演示饥饿变色/闪烁）;氧气在循环末段下潜（演示氧气条出现）;
 * 经验进度循环推进。各部件渲染时经 {@link #active} 分支读取这里的模拟值。
 */
public final class HudPreviewState {
    public static boolean active;
    public static float health = 20f, maxHealth = 20f;
    public static float absorption;
    public static int food = 20, armor = 6;
    /** 预览饱和度（饱食度条金段演示;模拟饱食消耗与扣除平滑） */
    public static float saturation = 5f;
    /** 预览消耗值 0..4（临近扣除预告演示） */
    public static float exhaustion;
    public static int air = 300, maxAir = 300;
    /** 兼容变量预览模拟值（渴/体力）——设置页模板预览显示确定值 */
    public static float thirst = 17f, thirstMax = 20f, stamina = 60f, staminaMax = 100f;
    public static int xpLevel = 7;
    public static float xpProgress;

    /** 预览专用的 BarFx 状态键：设置界面打开时真实 HUD 仍在底层渲染（真实血量）并与预览
     *  共用同一玩家 ID 的动画状态——两个目标每帧互相拉扯,平滑值卡在中间"掉不下去"。
     *  预览期间改用此独立键,与真实 HUD 的动画状态彻底隔离。 */
    private static final int PREVIEW_FX_KEY = -999_999_999;
    /** 实机大偏移键基址（饱食度/饱和度平滑用,与生命/吸收小负数键空间隔离） */
    private static final int FX_BASE = 1_000_000_000;

    /** BarFx 状态键：预览激活时用独立键,否则用实体真实 ID */
    public static int fxKey(int entityId) {
        return active ? PREVIEW_FX_KEY : entityId;
    }

    /** 吸收段专用 BarFx 键（与生命键互不干扰）：预览用独立负键,实机用 -(entityId+1)（实体 ID 非负,不冲突） */
    public static int fxKeyAbs(int entityId) {
        return active ? PREVIEW_FX_KEY - 1 : -(entityId + 1);
    }

    /** 饱食度平滑键（独立负空间,与生命/吸收互不干扰） */
    public static int fxKeyFood(int entityId) {
        return active ? PREVIEW_FX_KEY - 2 : -(FX_BASE + entityId * 4);
    }

    /** 饱和度平滑键 */
    public static int fxKeySat(int entityId) {
        return active ? PREVIEW_FX_KEY - 3 : -(FX_BASE + entityId * 4 + 1);
    }

    private static final long CYCLE = 6000L;
    private static final long[] BIRTHS = {400, 1300, 2200, 3100, 4000};

    private HudPreviewState() {}

    public static void update(long now) {
        long t = now % CYCLE;
        long idx = now / CYCLE;
        float hp = 20f;
        int foodV = 20;
        for (int i = 0; i < BIRTHS.length; i++) {
            long age = t - BIRTHS[i];
            if (age < 0) break;
            int dmg = 2 + (int) ((idx * 31L + i * 17L) % 6); // 2..7
            hp = Math.max(3f, hp - dmg);
            foodV = Math.max(4, foodV - 3);
        }
        // 回血阶段吃金苹果：3HP 保持到 5200（低血脉冲演示窗口）,5200-5900 平滑回升至满,
        // 全程 4 点吸收——演示部分血量吸收段 + 满血容量扩展;5900-6000 满血+吸收定格
        if (t >= 5200 && t < 5900) {
            hp = 3f + (t - 5200) / 700f * 17f;
            absorption = 4;
        } else if (t >= 5900) {
            absorption = 4;
        } else {
            absorption = 0;
        }
        health = hp;
        food = foodV;
        armor = 6;
        // 饱和度随循环前段衰减（演示饱食度条金段平滑收缩——像吸收段被消耗）
        saturation = Math.max(0f, 5f - t / 500f);
        // 消耗值 2 秒循环累积到 4+（演示临近扣除的预告呼吸,以及满 4 后的平滑回落）
        exhaustion = (t % 2000) / 2000f * 4.2f;
        air = t >= 4200 ? (int) (300 - Math.min(240, (t - 4200) / 1800f * 240)) : 300;
        xpLevel = 7;
        xpProgress = t / (float) CYCLE;
    }
}
