package com.z80z99.z80zhealthbar.mixin;

import com.z80z99.z80zhealthbar.network.packets.ExhaustionSyncPacket;
import com.z80z99.z80zhealthbar.network.packets.SaturationSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 追踪食物数据变化，同步到客户端 */
@Mixin(FoodData.class)
public class FoodDataMixin {

    @Shadow private float saturationLevel;
    @Shadow private float exhaustionLevel;

    private float lastSyncedSaturation = -1;
    private float lastSyncedExhaustion = -1;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp) {
            if (Math.abs(saturationLevel - lastSyncedSaturation) > 0.01f) {
                lastSyncedSaturation = saturationLevel;
                SaturationSyncPacket.sync(sp, saturationLevel);
            }
            if (Math.abs(exhaustionLevel - lastSyncedExhaustion) > 0.01f) {
                lastSyncedExhaustion = exhaustionLevel;
                ExhaustionSyncPacket.sync(sp, exhaustionLevel);
            }
        }
    }
}
