package com.z80z99.z80zhealthbar.status;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 实体状态快照（任务书 3.5 统一数据层）。
 *
 * <p>数据采集与视觉渲染解耦：所有样式渲染器只读快照，不直接触碰实体。
 * 无法获取的数据以 {@code hasX()==false} 标记，绝不将未知值伪装成 0。
 */
public final class EntityStatusSnapshot {

    public final int entityId;
    public final ResourceLocation entityType;
    public final String displayName;

    public final float health;
    public final float maxHealth;
    public final float absorption;

    public final int armor;          // -1 = 不可用
    public final int armorToughness; // -1 = 不可用

    public final int airSupply;
    public final int maxAirSupply;
    /** 眼睛在水里（原版 Entity.isUnderWater;与玩家 HUD 空气条同一判据）——
     *  氧气行按原版对玩家的规则显示：眼睛在水里 或 空气未满 */
    public final boolean eyeInWater;

    public final boolean poisoned;
    public final boolean withered;
    public final boolean frozen;
    public final boolean regenerating;
    public final boolean aggro;      // 仅 Mob 有意义

    public final double distanceSqr;
    public final float entityHeight;
    public final boolean dying;      // 正在播放死亡动画
    public final float deathProgress; // 0..1
    public final int hurtTime;       // 受伤动画剩余 tick（客户端同步，0 = 未在受伤硬直）

    private EntityStatusSnapshot(Builder b) {
        this.entityId = b.entityId;
        this.entityType = b.entityType;
        this.displayName = b.displayName;
        this.health = b.health;
        this.maxHealth = b.maxHealth;
        this.absorption = b.absorption;
        this.armor = b.armor;
        this.armorToughness = b.armorToughness;
        this.airSupply = b.airSupply;
        this.maxAirSupply = b.maxAirSupply;
        this.eyeInWater = b.eyeInWater;
        this.poisoned = b.poisoned;
        this.withered = b.withered;
        this.frozen = b.frozen;
        this.regenerating = b.regenerating;
        this.aggro = b.aggro;
        this.distanceSqr = b.distanceSqr;
        this.entityHeight = b.entityHeight;
        this.dying = b.dying;
        this.deathProgress = b.deathProgress;
        this.hurtTime = b.hurtTime;
    }

    public boolean hasArmor() { return armor >= 0; }
    public boolean hasToughness() { return armorToughness > 0; }
    /** 空气未满（掉氧中/回氧中）——注意不等于在水里：亡灵/水生生物在水下也不掉氧 */
    public boolean isUnderwater() { return airSupply < maxAirSupply; }
    public boolean isFullHealth() {
        return health >= maxHealth && absorption <= 0;
    }

    /** 血量比例（含吸收），0..1 */
    public float healthRatio() {
        float total = health + Math.max(0, absorption);
        return maxHealth <= 0 ? 0 : Math.min(1f, total / (maxHealth + Math.max(0, absorption)));
    }

    /** 纯生命比例（不含吸收），0..1 */
    public float plainHealthRatio() {
        return maxHealth <= 0 ? 0 : Math.min(1f, health / maxHealth);
    }

    /** 从实体采集快照。只读，不修改实体任何状态。 */
    public static EntityStatusSnapshot capture(LivingEntity entity, double distanceSqr) {
        Builder b = new Builder();
        b.entityId = entity.getId();
        b.entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Component name = entity.getDisplayName();
        b.displayName = name == null ? "" : name.getString();
        b.maxHealth = Math.max(1, entity.getMaxHealth());
        // 展示层夹取:原版 setSize 缩小体型(史莱姆分裂)只改 max 不重置血量,
        // 同步窗口内 health>max(如小史莱姆 20/1),按满血展示而非超上限数值
        b.health = Math.min(Math.max(0, entity.getHealth()), b.maxHealth);
        b.absorption = Math.max(0, entity.getAbsorptionAmount());

        var armorAttr = entity.getAttribute(Attributes.ARMOR);
        b.armor = armorAttr == null ? -1 : (int) armorAttr.getValue();
        var toughAttr = entity.getAttribute(Attributes.ARMOR_TOUGHNESS);
        b.armorToughness = toughAttr == null ? -1 : (int) toughAttr.getValue();

        b.airSupply = entity.getAirSupply();
        b.maxAirSupply = Math.max(1, entity.getMaxAirSupply());
        b.eyeInWater = entity.isUnderWater();

        b.poisoned = entity.hasEffect(MobEffects.POISON);
        b.withered = entity.hasEffect(MobEffects.WITHER);
        b.frozen = entity.isFullyFrozen();
        b.regenerating = entity.hasEffect(MobEffects.REGENERATION);
        b.aggro = entity instanceof Mob mob && mob.isAggressive();

        b.distanceSqr = distanceSqr;
        b.entityHeight = entity.getBbHeight();
        b.dying = entity.isDeadOrDying();
        b.deathProgress = entity.deathTime >= 19 ? 1f : entity.deathTime / 20f;
        b.hurtTime = entity.hurtTime;
        return new EntityStatusSnapshot(b);
    }

    private static final class Builder {
        int entityId;
        ResourceLocation entityType;
        String displayName = "";
        float health, maxHealth, absorption;
        int armor = -1, armorToughness = -1;
        int airSupply, maxAirSupply = 1;
        boolean eyeInWater;
        boolean poisoned, withered, frozen, regenerating, aggro;
        double distanceSqr;
        float entityHeight;
        boolean dying;
        float deathProgress;
        int hurtTime;
    }

    /**
     * 设置页实时预览用：构造无实体绑定的模拟快照。
     *
     * @param entityHeight 实体高度（方块）——血条锚点 = 该高度 + 各样式自身的高度偏移
     * @param armor        传 -1 表示无护甲行
     * @param toughness    传 -1 表示无韧性行
     * @param hurt         模拟"正处于受伤硬直"（触发血条动态效果的受伤闪白）
     * @param air          模拟氧气（上限固定 300）。必须小于 300 才能预览氧气行——
     *                     空气满时按原版语义氧气行/气泡不显示（此前恒 300,氧气相关设置看不到预览效果）
     */
    public static EntityStatusSnapshot preview(String displayName, float entityHeight, float health, float maxHealth,
                                               float absorption, int armor, int toughness, boolean hurt, int air) {
        Builder b = new Builder();
        b.entityId = -1;
        b.entityType = new ResourceLocation("minecraft", "player");
        b.displayName = displayName;
        b.health = health;
        b.maxHealth = maxHealth;
        b.absorption = absorption;
        b.armor = armor;
        b.armorToughness = toughness;
        b.airSupply = Math.max(0, Math.min(300, air));
        b.maxAirSupply = 300;
        b.eyeInWater = air < 300; // 预览:空气未满即视为在水中
        b.entityHeight = entityHeight;
        b.hurtTime = hurt ? 10 : 0;
        return new EntityStatusSnapshot(b);
    }
}
