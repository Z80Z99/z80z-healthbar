package com.z80z99.z80zhealthbar.mixin;

import com.z80z99.z80zhealthbar.network.packets.AbsorptionSyncPacket;
import com.z80z99.z80zhealthbar.network.packets.DamagePopupPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 追踪实体吸收值变化，同步到客户端；采集伤害跳字精确值 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Shadow private float absorptionAmount;
    private float lastSyncedAbsorption = -1;

    @Unique private float z80z$popupBeforeHealth;
    @Unique private float z80z$popupBeforeAbsorption;

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void onAiStep(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!self.level().isClientSide() && Math.abs(absorptionAmount - lastSyncedAbsorption) > 0.5f) {
            lastSyncedAbsorption = absorptionAmount;
            AbsorptionSyncPacket.sync(self, absorptionAmount);
        }
    }

    // ===== 伤害跳字：actuallyHurt 仅在真实扣血路径被调用（无敌帧判定发生在 hurt() 更早处），
    // 前后差值即"减伤后最终伤害"，吸收消耗部分单独拆出供 APEX 主题分色 =====

    @Inject(method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V", at = @At("HEAD"))
    private void z80z$captureBeforeHurt(DamageSource source, float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.level().isClientSide()) return;
        z80z$popupBeforeHealth = self.getHealth();
        z80z$popupBeforeAbsorption = self.getAbsorptionAmount();
    }

    @Inject(method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V", at = @At("TAIL"))
    private void z80z$onActuallyHurt(DamageSource source, float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.level().isClientSide()) return;
        float healthDamage = z80z$popupBeforeHealth - self.getHealth();
        float absorbed = Math.max(0f, z80z$popupBeforeAbsorption - self.getAbsorptionAmount());
        if (healthDamage + absorbed < 0.01f) return;
        // 攻击者(含弹射物主人)是玩家才带 FLAG_PLAYER:准星标记/音效仅反馈玩家本人造成的伤害,
        // 环境伤害(燃烧/摔落/生物互殴)只出跳字,不触发命中反馈
        boolean playerCaused = source.getEntity() instanceof net.minecraft.server.level.ServerPlayer;
        byte flags = (byte) ((self.isDeadOrDying() ? DamagePopupPacket.FLAG_KILLED : 0)
                | (playerCaused ? DamagePopupPacket.FLAG_PLAYER : 0));
        DamagePopupPacket.sync(self, healthDamage, absorbed, self.getMaxHealth(),
                DamagePopupPacket.categoryOf(source), flags);
    }
}
