package com.z80z99.z80zhealthbar.mobdisplay;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 实体血条动态效果状态缓存（客户端，按实体 ID）。
 *
 * <p>残影模型（2026-10-05 重做,单区域 + 透明度渐隐,可证明无残留/无跳格/无治疗误画）：
 * <ul>
 *   <li>掉血：残影区域上缘 = 掉血前血量位置（preHit）,下缘 = 当前显示填充;
 *       渐隐计时重开（重复掉血重新计时）;</li>
 *   <li>治疗：血量回升,残影区域立即清空（回升的部分不是损失）;</li>
 *   <li>渐隐：区域透明度在 {@link #GHOST_MS} 内线性降到 0,归零即清空——固定时长硬收敛,
 *       无渐近尾巴。</li>
 * </ul>
 * display：平滑后的填充比例（指数逼近,时间常数 {@link #SMOOTH_MS}）；
 * flash：受伤闪白强度；heal：治疗脉冲强度。条目 {@link #STALE_MS} 未被渲染即回收。
 */
public final class BarFx {

    /** display = 平滑填充比例;preHit = 残影区域上缘（掉血前血量比例）;ghostAlpha = 残影区域不透明度(0..1) */
    public record State(float display, float preHit, float ghostAlpha, float flash, float heal) {}

    private static final float SMOOTH_MS = 90f;
    private static final float GHOST_MS = 420f;
    private static final float FLASH_MS = 260f;
    private static final long STALE_MS = 3000L;

    private static final Map<Integer, Fx> FX = new HashMap<>();

    private BarFx() {}

    private static final class Fx {
        float display;
        float preHit;
        long hitAt = -1;
        float flash;
        float heal;
        float lastTarget;
        long lastMs;
        long lastSeen;

        Fx(float ratio, long now) {
            display = ratio;
            preHit = ratio;
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
        // 收敛吸附：指数平滑渐近逼近,稳态永远略小于目标（如 0.99995）——
        // 截断取整会把满值裁掉 1px（条填不满）。差值小于半像素量级时直接吸附到目标。
        if (Math.abs(target - fx.display) < 0.0005f) fx.display = target;

        // 残影区域维护：掉血 → 上缘抬到掉血前位置并重开渐隐;治疗 → 区域清空
        float prevTarget = fx.lastTarget;
        if (target > prevTarget + 1e-4f) {
            fx.preHit = fx.display; // 治疗：回升的部分不是损失,区域立即消失
            fx.hitAt = -1;
        } else if (target < prevTarget - 1e-4f) {
            fx.preHit = Math.max(fx.preHit, prevTarget); // 掉血：上缘抬到掉血前血量
            fx.hitAt = now;                              // 渐隐重新计时
        }
        fx.lastTarget = target;

        float ghostAlpha = 0f;
        if (fx.hitAt >= 0) {
            ghostAlpha = Math.max(0f, 1f - (now - fx.hitAt) / GHOST_MS);
            if (ghostAlpha <= 0f) {
                fx.preHit = fx.display; // 渐隐完成：区域清空,不残留
                fx.hitAt = -1;
            }
        }

        if (hurt) fx.flash = 1f;
        else fx.flash = Math.max(0f, fx.flash - dt / FLASH_MS);
        // 治疗脉冲：target 相对上一帧上升即触发（受伤由 hurtTime 驱动，不走这里）。
        // 修复：此前与 fx.lastTarget 比较——但它在第 79 行已被覆盖为当前 target,
        // 恒为 target > target 永假,heal 从未置 1（治疗泛光/数字变绿从未生效的根因）;
        // 阈值 0.0005 使缓慢自然恢复（每帧 +0.0025）也能触发持续泛光
        if (target > prevTarget + 5e-4f) fx.heal = 1f;
        else fx.heal = Math.max(0f, fx.heal - dt / FLASH_MS);

        if (FX.size() > 512) sweep(now);
        return new State(fx.display, fx.preHit, ghostAlpha, fx.flash, fx.heal);
    }

    private static void sweep(long now) {
        Iterator<Map.Entry<Integer, Fx>> it = FX.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastSeen > STALE_MS) it.remove();
        }
    }
}
