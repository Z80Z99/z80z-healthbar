package com.z80z99.z80zhealthbar.mobdisplay;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.VisibilityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * 组合式显示规则（任务书 3.4：规则可组合而非相互覆盖）。
 * 返回 0 = 显示；>0 为跳过原因码（供调试输出）。
 */
public final class MobVisibilityChecker {
    private MobVisibilityChecker() {}

    // ---- 仇恨检测记忆（2026-10-09:mod 生物修复）----
    // Mob.isAggressive 只是同步标志位,仅原版 MeleeAttackGoal 会置位;mod 自定义攻击 AI 不置位,
    // getTarget 又不同步（客户端恒 null）——此前"仅仇恨中"对 mod 生物完全失效。
    // 客户端可用的三判据:①isAggressive 标志 ②本地玩家的 getLastHurtByMob(它最近打了我,本地权威)
    // ③该实体血量最近下降(我/队友打过它,近似)。
    private static final java.util.Map<Integer, float[]> AGGRO_HEALTH = new java.util.HashMap<>(); // {lastHealth, lastSeenMs}
    private static final java.util.Map<Integer, Long> AGGRO_DAMAGED_AT = new java.util.HashMap<>();
    /** 受击记忆窗口:血量下降后视为"交战中"的时长 */
    private static final long AGGRO_MEMORY_MS = 10_000L;

    /** 渲染管线每帧调用:记录实体血量下降时刻（仇恨判据③;来源不区分,近似"最近被攻击过"） */
    public static void noteHealthForAggro(int entityId, float health, long nowMs) {
        if (AGGRO_HEALTH.size() > 2048) { // 容量封顶:按 lastSeen 记龄清理(30s 未见)
            // 注意不能按"无 DAMAGED_AT 即删"——刚见面/未受伤的实体会被删掉,下一帧又插回,
            // 清理形同虚设且 DAMAGED_AT 与 HEALTH 互相牵连(1.0.118 引入,自查发现)
            AGGRO_HEALTH.entrySet().removeIf(e -> nowMs - e.getValue()[1] > AGGRO_MEMORY_MS * 3);
            AGGRO_DAMAGED_AT.keySet().retainAll(AGGRO_HEALTH.keySet());
        }
        float[] st = AGGRO_HEALTH.computeIfAbsent(entityId, k -> new float[]{-1f, nowMs});
        if (st[0] >= 0f && health < st[0] - 1e-4f) AGGRO_DAMAGED_AT.put(entityId, nowMs);
        st[0] = health;
        st[1] = nowMs;
    }

    /** 实体当前是否"与玩家交战"（客户端三判据,见字段注释） */
    static boolean isAggroOnPlayer(Player player, LivingEntity entity, long nowMs) {
        if (entity instanceof Mob mob && mob.isAggressive()) return true;
        if (player.getLastHurtByMob() == entity
                && player.tickCount - player.getLastHurtByMobTimestamp() < 200) return true;
        Long at = AGGRO_DAMAGED_AT.get(entity.getId());
        return at != null && nowMs - at < AGGRO_MEMORY_MS;
    }

    public static int check(LivingEntity entity, Player player, double distanceSqr) {
        var cfg = ConfigManager.getConfig().visibility;

        // 1. 旁观模式 / 实体死亡移除。
        // "死亡动画中"（血量 0 但未移除）**放行**——碎裂/死亡收缩/死亡渐隐全靠 dying 快照提交,
        // 此前 !isAlive() 一律踢掉使这些效果全部失效（实测"死亡玻璃碎片不生效"的根因）。
        // 仅当任一死亡效果可接管时才放行;全关时保持旧行为（立即消失）
        if (player.isSpectator()) return 13;
        if (entity.isRemoved()) return 1;
        if (!entity.isAlive()) {
            var fx = ConfigManager.getConfig().dynamicFx;
            boolean deathFx = fx.enabled && (fx.shatter || fx.deathShrink);
            if (!deathFx && !cfg.fadeOnDeath) return 1;
        }

        // 2. 距离超限（全局统一规则）。水中生物可见距离减半——血条改走 GUI 通道后无深度遮挡,
        //    水下远距离出现显得突兀（用户实测反馈"还没看到实体就出现血条"）
        double maxDist = cfg.maxDistance * (entity.isUnderWater() ? 0.5 : 1.0);
        if (distanceSqr > maxDist * maxDist) return 2;

        // 3-4. 隐身
        if (entity.isInvisibleTo(player)) return 3;
        if (entity.isInvisible()) return 4;

        // 5. 自身
        if (!cfg.showOnSelf && entity == player) return 5;

        // 6. 其他玩家
        if (!cfg.showOnPlayers && entity instanceof Player) return 6;

        // 6b. 敌对/友好分类（组合式：两个都关 = 全关）
        boolean hostile = entity instanceof Enemy;
        if (hostile && !cfg.showOnHostile) return 14;
        if (!hostile && entity instanceof Mob && !cfg.showOnPassive) return 15;

        // 7. Boss（血量阈值近似，见任务书"Boss"类目）
        if (!cfg.showOnBoss && isBoss(entity)) return 7;

        // 8. 盔甲架
        if (!cfg.showOnArmorStands && entity instanceof ArmorStand) return 11;

        // 9-10. 满血 ± 吸收
        boolean full = entity.getHealth() >= entity.getMaxHealth();
        if (full && entity.getAbsorptionAmount() > 0 && !cfg.showOnFullHealthWithAbsorption) return 8;
        if (full && entity.getAbsorptionAmount() <= 0 && !cfg.showOnFullHealthWithoutAbsorption) return 9;

        // 10b. 组合条件：仅受伤 / 仅仇恨 / 仅准星
        if (cfg.showDamaged && !(entity.getHealth() < entity.getMaxHealth())) return 16;
        if (cfg.showOnAggro && !isAggroOnPlayer(player, entity, System.currentTimeMillis())) return 17;
        if (cfg.showHoveredMob) {
            Entity crosshair = Minecraft.getInstance().crosshairPickEntity;
            if (crosshair != entity) return 18;
        }

        // 10c. 视线遮挡检测（性能敏感，默认关闭）
        if (cfg.showOnlyWhenVisible && !player.hasLineOfSight(entity)) return 19;

        // 11. 黑名单
        if (cfg.isBlacklisted(entity.getType())) return 10;

        // 12. MobPlaquesSelector 双列表（disallowed 优先）
        if (!MobPlaquesSelector.isAllowed(entity, cfg.parsedAllowed, cfg.parsedDisallowed)) return 12;

        return 0;
    }

    public static boolean shouldRender(LivingEntity entity, double distanceSqr) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        return check(entity, player, distanceSqr) == 0;
    }

    /** 跳过原因码 → 可读名（F3 诊断用：直接看出"血条为什么没显示"） */
    public static String reasonName(int reason) {
        return switch (reason) {
            case 0 -> "visible";
            case 1 -> "removed/dead";
            case 2 -> "over maxDistance";
            case 3, 4 -> "invisible";
            case 5 -> "self (showOnSelf=false)";
            case 6 -> "other player (showOnPlayers=false)";
            case 7 -> "boss (showOnBoss=false)";
            case 8, 9 -> "full-health rule";
            case 10 -> "blacklisted";
            case 11 -> "armor stand";
            case 12 -> "selector not allowed";
            case 13 -> "spectator";
            case 14 -> "hostile filtered";
            case 15 -> "passive filtered";
            case 16 -> "showDamaged-only";
            case 17 -> "showOnAggro-only (no aggro: flag/lastHurt/recentDamage)";
            case 18 -> "showHoveredMob-only";
            case 19 -> "no line of sight";
            default -> "code " + reason;
        };
    }

    private static boolean isBoss(LivingEntity entity) {
        return entity.getMaxHealth() >= 100;
    }
}
