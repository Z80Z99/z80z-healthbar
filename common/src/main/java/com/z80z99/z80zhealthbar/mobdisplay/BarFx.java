package com.z80z99.z80zhealthbar.mobdisplay;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 实体血条动态效果状态缓存（客户端，按实体 ID）。
 *
 * <ul>
 *   <li>display：平滑后的填充比例（指数逼近目标，时间常数 {@link #SMOOTH_MS}）；</li>
 *   <li>ghost：伤害残影比例——掉血时停在原位慢速收缩，治疗时立即跟上；display ≤ ghost，
 *       两者之间的区段即"刚损失的血量"；</li>
 *   <li>flash：受伤闪白强度（hurtTime 触发置 1，按 {@link #FLASH_MS} 线性衰减）；</li>
 *   <li>heal：治疗脉冲强度（血量上限提高时置 1，同样衰减）——驱动数字变绿/滚动。</li>
 * </ul>
 * 条目 {@link #STALE_MS} 未被渲染即回收，防止长驻世界内存膨胀。
 */
public final class BarFx {

    public record State(float display, float ghost, float flash, float heal) {}

    private static final float SMOOTH_MS = 90f;
    private static final float GHOST_MS = 420f;
    private static final float FLASH_MS = 260f;
    private static final long STALE_MS = 3000L;

    private static final Map<Integer, Fx> FX = new HashMap<>();

    private BarFx() {}

    private static final class Fx {
        float display;
        float ghost;
        float flash;
        float heal;
        float lastTarget;
        long lastMs;
        long lastSeen;

        Fx(float ratio, long now) {
            display = ratio;
            ghost = ratio;
            lastTarget = ratio;
            lastMs = now;
            lastSeen = now;
        }
    }

    /** 每帧调用：target = 当前真实血量比例，hurt = 实体正处于受伤硬直（hurtTime > 0）。 */
    public static State tick(int entityId, float target, boolean hurt, long now) {
        Fx fx = FX.get(entityId);
        if (fx == null) {
            fx = new Fx(target, now);
            FX.put(entityId, fx);
        }
        float dt = Math.min(100f, Math.max(0f, now - fx.lastMs));
        fx.lastMs = now;
        fx.lastSeen = now;

        fx.display += (target - fx.display) * (1f - (float) Math.exp(-dt / SMOOTH_MS));
        if (target > fx.ghost) {
            fx.ghost = target; // 治疗：残影立即跟上
        } else {
            fx.ghost += (target - fx.ghost) * (1f - (float) Math.exp(-dt / GHOST_MS));
        }
        if (hurt) fx.flash = 1f;
        else fx.flash = Math.max(0f, fx.flash - dt / FLASH_MS);
        // 治疗脉冲：血量上限明显提高时触发（受伤由 hurtTime 驱动，不走这里）
        if (target > fx.lastTarget + 0.01f) fx.heal = 1f;
        else fx.heal = Math.max(0f, fx.heal - dt / FLASH_MS);
        fx.lastTarget = target;

        if (FX.size() > 512) sweep(now);
        return new State(fx.display, fx.ghost, fx.flash, fx.heal);
    }

    private static void sweep(long now) {
        Iterator<Map.Entry<Integer, Fx>> it = FX.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastSeen > STALE_MS) it.remove();
        }
    }
}
