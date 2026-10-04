package com.z80z99.z80zhealthbar.mobdisplay;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.network.packets.DamagePopupPacket;
import com.z80z99.z80zhealthbar.status.EntityStatusSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 伤害跳字队列（仅渲染线程/网络主线程访问，单线程无锁）。
 *
 * <ul>
 *   <li>精确路径：{@link #pushFromServer}（服务端 actuallyHurt 计算真实掉血后经包送达）；</li>
 *   <li>估算路径：{@link #onSnapshot}（无服务端模组时按客户端血量差值 + hurtTime 推断）；</li>
 *   <li>去重：收到服务端包的实体在 10 秒内不走估算，避免同一跳双重显示。</li>
 *   <li>运动方式：RISE/ARC/STACK 走 {@link #POPUPS} 队列；
 *       CUMULATIVE 走 {@link #CUM_TOTAL} 累加器（窗口内伤害求和，单一文本）。</li>
 * </ul>
 *
 * <p>每条跳字在推入时冻结世界坐标锚点，由 GameRenderer.renderLevel TAIL 的全局通道渲染。
 * 全局上限 64 条，刷怪塔场景防渲染雪崩。
 */
public final class DamagePopupManager {

    /** 一条跳字：healthDamage = 生命扣减；absorbed = 吸收抵消部分；count = 合并段数；
     *  x/headY/feetY/z = 推入时冻结的世界锚点（headY = 脚部 y + 实体高度，feetY 供坠落类落地） */
    public record Popup(long birthMillis, float healthDamage, float absorbed,
                        byte category, byte flags, int count, float maxHealth, int seed,
                        double x, double headY, double feetY, double z, int entityId, boolean estimated) {}

    public static final byte FLAG_KILLED = DamagePopupPacket.FLAG_KILLED;
    /** 全局同屏上限 */
    private static final int GLOBAL_CAP = 64;
    /** 服务端在场标记时长 */
    private static final long SERVER_TRACKED_MS = 10_000L;

    private static final Map<Integer, Deque<Popup>> POPUPS = new HashMap<>();
    private static final Map<Integer, Float> LAST_HEALTH = new HashMap<>();
    private static final Map<Integer, Long> SERVER_TRACKED = new HashMap<>();
    /** 实体最近一次渲染帧的锚点（渲染帧刷新；击杀时用它冻结位置）:
     *  [0]=x [1]=headY [2]=feetY [3]=z */
    private static final Map<Integer, double[]> LAST_ANCHOR = new HashMap<>();
    /** CUMULATIVE 运动方式的每实体伤害总和 */
    private static final Map<Integer, Float> CUM_TOTAL = new HashMap<>();
    private static final Map<Integer, Long> CUM_LAST = new HashMap<>();
    private static final Map<Integer, Byte> CUM_CAT = new HashMap<>();
    private static final Map<Integer, Byte> CUM_FLAGS = new HashMap<>();
    private static final Map<Integer, Integer> CUM_COUNT = new HashMap<>();
    private static final Map<Integer, Float> CUM_MAX = new HashMap<>();
    private static long lastHitMillis = -10_000L;
    private static long lastKillMillis = -10_000L;

    private DamagePopupManager() {}

    // ================= 数据入口 =================

    /** 服务端精确包到达（客户端主线程） */
    public static void pushFromServer(int entityId, float healthDamage, float absorbed,
                                      byte category, byte flags, float maxHealth, LivingEntity entity) {
        boolean wasTracked = SERVER_TRACKED.getOrDefault(entityId, 0L) > System.currentTimeMillis();
        SERVER_TRACKED.put(entityId, System.currentTimeMillis() + SERVER_TRACKED_MS);
        if (isCumulative()) {
            accumulate(entityId, healthDamage + absorbed, category, flags, maxHealth);
            return;
        }
        if (!wasTracked) purgeEstimated(entityId);
        double[] anchor = LAST_ANCHOR.get(entityId);
        if (anchor == null && entity != null) {
            anchor = new double[]{entity.getX(), entity.getY() + entity.getBbHeight(),
                    entity.getY(), entity.getZ()};
        }
        if (anchor == null) return; // 从未渲染过且实体已移除，无锚点可用
        push(entityId, healthDamage, absorbed, category, flags, maxHealth, anchor, false);
    }

    /** 清除该实体最近 300ms 内的估算跳字——集成服务器下估算先于精确包一帧触发,
     *  同一跳会画两遍(视觉为打击瞬间闪烁),精确值到达时以精确值取代估算值 */
    private static void purgeEstimated(int entityId) {
        Deque<Popup> deque = POPUPS.get(entityId);
        if (deque == null) return;
        long now = System.currentTimeMillis();
        while (!deque.isEmpty()) {
            Popup last = deque.peekLast();
            if (last.estimated() && now - last.birthMillis() <= 300) {
                deque.removeLast();
            } else {
                break;
            }
        }
    }

    /** 渲染帧快照：更新血量基线与锚点缓存，并按需生成估算跳字/CUMULATIVE 累加 */
    public static void onSnapshot(EntityStatusSnapshot snap, double x, double feetY, double z) {
        var cfg = ConfigManager.getConfig().damagePopup;
        int id = snap.entityId;
        LAST_ANCHOR.put(id, new double[]{x, feetY + snap.entityHeight, feetY, z});
        Float last = LAST_HEALTH.get(id);
        LAST_HEALTH.put(id, snap.health);
        boolean cumulative = isCumulative();
        if (cumulative && !cfg.estimateWithoutServer) return;
        Long trackedUntil = SERVER_TRACKED.get(id);
        if (trackedUntil != null && trackedUntil > System.currentTimeMillis()) return;
        if (last == null) return;
        float drop = last - snap.health;
        // hurtTime > 0 排除回血/状态造成的非伤害性数值变化
        if (drop > 0.01f && snap.hurtTime > 0) {
            byte flags = (byte) (snap.dying ? FLAG_KILLED : 0);
            if (cumulative) {
                accumulate(id, drop, DamagePopupPacket.CAT_PHYSICAL, flags, snap.maxHealth);
            } else {
                push(id, drop, 0f, DamagePopupPacket.CAT_PHYSICAL, flags, snap.maxHealth,
                        LAST_ANCHOR.get(id), true);
            }
        }
    }

    /** CUMULATIVE 运动方式是否启用 */
    private static boolean isCumulative() {
        return ConfigManager.getConfig().damagePopup.motionParsed().equals("CUMULATIVE");
    }

    private static void push(int entityId, float healthDamage, float absorbed,
                             byte category, byte flags, float maxHealth, double[] anchor, boolean estimated) {
        var cfg = ConfigManager.getConfig().damagePopup;
        long now = System.currentTimeMillis();
        Deque<Popup> deque = POPUPS.computeIfAbsent(entityId, k -> new ArrayDeque<>());
        long mergeWindow = cfg.mergeWindowTicks * 50L;
        Popup last = deque.peekLast();
        if (mergeWindow > 0 && last != null && now - last.birthMillis() <= mergeWindow) {
            deque.removeLast();
            deque.addLast(new Popup(last.birthMillis(),
                    last.healthDamage() + healthDamage, last.absorbed() + absorbed,
                    category, (byte) (last.flags() | flags), last.count() + 1,
                    Math.max(last.maxHealth(), maxHealth), last.seed(),
                    anchor[0], anchor[1], anchor[2], anchor[3], entityId,
                    last.estimated() && estimated));
        } else {
            deque.addLast(new Popup(now, healthDamage, absorbed, category, flags, 1,
                    maxHealth, entityId * 31 + (int) (now & 0xFFFF),
                    anchor[0], anchor[1], anchor[2], anchor[3], entityId, estimated));
        }
        while (deque.size() > cfg.maxPerEntity) deque.removeFirst();
        pruneGlobal();
        // 命中反馈(准星标记+音效)仅对玩家造成的伤害触发;环境伤害(燃烧/摔落/互殴)只出跳字
        if ((flags & DamagePopupPacket.FLAG_PLAYER) != 0) {
            lastHitMillis = now;
            if ((flags & FLAG_KILLED) != 0) lastKillMillis = now;
            playFeedback((flags & FLAG_KILLED) != 0);
        }
    }

    /** CUMULATIVE 累加:窗口内伤害求和,单一文本随最后命中时间淡出 */
    private static void accumulate(int entityId, float amount, byte category, byte flags, float maxHealth) {
        long now = System.currentTimeMillis();
        CUM_TOTAL.merge(entityId, amount, Float::sum);
        CUM_COUNT.merge(entityId, 1, Integer::sum);
        CUM_LAST.put(entityId, now);
        CUM_CAT.put(entityId, category);
        CUM_FLAGS.put(entityId, (byte) (CUM_FLAGS.getOrDefault(entityId, (byte) 0) | flags));
        CUM_MAX.merge(entityId, maxHealth, Float::max);
        if ((flags & DamagePopupPacket.FLAG_PLAYER) != 0) {
            lastHitMillis = now;
            if ((flags & FLAG_KILLED) != 0) lastKillMillis = now;
            playFeedback((flags & FLAG_KILLED) != 0);
        }
    }

    // ================= 渲染查询 =================

    /** 全部存活跳字（顺带清理过期项），最老 → 最新；供全局渲染通道每帧调用 */
    public static List<Popup> allLive(long lifetimeMs) {
        long now = System.currentTimeMillis();
        List<Popup> out = new ArrayList<>();
        Iterator<Map.Entry<Integer, Deque<Popup>>> it = POPUPS.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Popup> deque = it.next().getValue();
            while (!deque.isEmpty() && now - deque.peekFirst().birthMillis() > lifetimeMs) {
                deque.removeFirst();
            }
            if (deque.isEmpty()) { it.remove(); continue; }
            out.addAll(deque);
        }
        return out;
    }

    /** CUMULATIVE 运动方式的存活记录(过期即清除);每实体至多一条 */
    public static List<Popup> cumulativeLive(long lifetimeMs) {
        long now = System.currentTimeMillis();
        List<Popup> out = new ArrayList<>();
        Iterator<Map.Entry<Integer, Long>> it = CUM_LAST.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            int id = e.getKey();
            if (now - e.getValue() > lifetimeMs) {
                it.remove();
                CUM_TOTAL.remove(id);
                CUM_CAT.remove(id);
                CUM_FLAGS.remove(id);
                CUM_COUNT.remove(id);
                CUM_MAX.remove(id);
                continue;
            }
            double[] anchor = LAST_ANCHOR.get(id);
            if (anchor == null) continue;
            out.add(new Popup(e.getValue(), CUM_TOTAL.getOrDefault(id, 0f), 0f,
                    CUM_CAT.getOrDefault(id, DamagePopupPacket.CAT_PHYSICAL),
                    CUM_FLAGS.getOrDefault(id, (byte) 0),
                    CUM_COUNT.getOrDefault(id, 1), CUM_MAX.getOrDefault(id, 20f),
                    id * 31 + (int) (now & 0xFFFF),
                    anchor[0], anchor[1], anchor[2], anchor[3], id, true));
        }
        return out;
    }

    /** 是否存在可绘制的存活跳字(供窗口门控,零分配) */
    public static boolean hasLive() {
        for (Deque<Popup> d : POPUPS.values()) if (!d.isEmpty()) return true;
        return !CUM_LAST.isEmpty();
    }

    public static long lastHitMillis() { return lastHitMillis; }

    public static long lastKillMillis() { return lastKillMillis; }

    // ================= 内部 =================

    private static void pruneGlobal() {
        int total = 0;
        for (Deque<Popup> deque : POPUPS.values()) total += deque.size();
        if (total <= GLOBAL_CAP) return;
        Iterator<Map.Entry<Integer, Deque<Popup>>> it = POPUPS.entrySet().iterator();
        while (it.hasNext() && total > GLOBAL_CAP) {
            Deque<Popup> deque = it.next().getValue();
            if (deque.isEmpty()) { it.remove(); continue; }
            deque.removeFirst();
            total--;
            if (deque.isEmpty()) it.remove();
        }
    }

    /** FPS 命中反馈音（客户端主线程）：击杀低音 ping，普通命中高频短 tick */
    private static void playFeedback(boolean killed) {
        var cfg = ConfigManager.getConfig().damagePopup;
        if (!cfg.hitMarkerSound) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        if (killed) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 0.3f));
        } else {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.7f, 0.25f));
        }
    }
}
