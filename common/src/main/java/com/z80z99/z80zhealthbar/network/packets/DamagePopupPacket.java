package com.z80z99.z80zhealthbar.network.packets;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.mobdisplay.DamagePopupManager;
import com.z80z99.z80zhealthbar.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * S2C: 伤害跳字精确值（服务端在 LivingEntity#actuallyHurt 后计算真实掉血）。
 * 数值语义：healthDamage = 实际扣减的生命值；absorbed = 被吸收值消耗的部分（APEX 主题分色显示）。
 * 常量类只含基础类型与字符串，双端均可安全加载；handle 仅客户端执行。
 */
public final class DamagePopupPacket {
    public static final ResourceLocation ID =
            new ResourceLocation(Z80ZHealthBar.MOD_ID, "damage_popup");

    /** 伤害类型分类（服务端按 DamageTypeTags 判定，客户端仅着色） */
    public static final byte CAT_PHYSICAL = 0;
    public static final byte CAT_PROJECTILE = 1;
    public static final byte CAT_FIRE = 2;
    public static final byte CAT_EXPLOSION = 3;
    public static final byte CAT_MAGIC = 4;
    public static final byte CAT_FALL = 5;

    public static final byte FLAG_KILLED = 1;
    /** 攻击者为玩家(准星命中标记与命中音效仅对玩家造成的伤害反馈) */
    public static final byte FLAG_PLAYER = 2;

    private DamagePopupPacket() {}

    public static void handle(Player player, FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        float healthDamage = buf.readFloat();
        float absorbed = buf.readFloat();
        float maxHealth = buf.readFloat();
        byte category = buf.readByte();
        byte flags = buf.readByte();
        if (player != null && player.level().getEntity(entityId) instanceof LivingEntity entity) {
            DamagePopupManager.pushFromServer(entityId, healthDamage, absorbed, category, flags, maxHealth, entity);
        }
    }

    public static void sync(LivingEntity entity, float healthDamage, float absorbed,
                            float maxHealth, byte category, byte flags) {
        if (entity.level().isClientSide()) return;
        for (var player : entity.level().players()) {
            if (player instanceof ServerPlayer sp && player.distanceToSqr(entity) < 64 * 64) {
                NetworkHandler.sendToPlayer(sp, ID, b -> {
                    b.writeVarInt(entity.getId());
                    b.writeFloat(healthDamage);
                    b.writeFloat(absorbed);
                    b.writeFloat(maxHealth);
                    b.writeByte(category);
                    b.writeByte(flags);
                });
            }
        }
    }

    /** 伤害类型 → 分类字节（注意：1.20.1 无 IS_MAGIC，用 WITCH_RESISTANT_TO 判定魔法系） */
    public static byte categoryOf(DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return CAT_EXPLOSION;
        if (source.is(DamageTypeTags.IS_LIGHTNING)) return CAT_EXPLOSION;
        if (source.is(DamageTypeTags.IS_FIRE)) return CAT_FIRE;
        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) return CAT_MAGIC;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return CAT_PROJECTILE;
        if (source.is(DamageTypeTags.IS_FALL)) return CAT_FALL;
        if (source.is(DamageTypeTags.IS_FREEZING)) return CAT_MAGIC;
        return CAT_PHYSICAL;
    }
}
