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
    public static int air = 300, maxAir = 300;
    public static int xpLevel = 7;
    public static float xpProgress;

    /** 预览专用的 BarFx 状态键：设置界面打开时真实 HUD 仍在底层渲染（真实血量）并与预览
     *  共用同一玩家 ID 的动画状态——两个目标每帧互相拉扯,平滑值卡在中间"掉不下去"。
     *  预览期间改用此独立键,与真实 HUD 的动画状态彻底隔离。 */
    private static final int PREVIEW_FX_KEY = -999_999_999;

    /** BarFx 状态键：预览激活时用独立键,否则用实体真实 ID */
    public static int fxKey(int entityId) {
        return active ? PREVIEW_FX_KEY : entityId;
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
        health = hp;
        food = foodV;
        armor = 6;
        air = t >= 4200 ? (int) (300 - Math.min(240, (t - 4200) / 1800f * 240)) : 300;
        xpLevel = 7;
        xpProgress = t / (float) CYCLE;
        // 回血阶段吃金苹果：演示吸收段（金色附加在生命填充之后）与吸收金心图标
        absorption = (t >= 4600 && t < 5800) ? 4 : 0;
    }
}
