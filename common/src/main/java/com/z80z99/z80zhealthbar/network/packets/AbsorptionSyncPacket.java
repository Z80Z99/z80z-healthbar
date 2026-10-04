package com.z80z99.z80zhealthbar.network.packets;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** S2C: 同步实体吸收值（服务端安装本 MOD 时生效，客户端无需装任何额外 MOD） */
public final class AbsorptionSyncPacket {
    public static final ResourceLocation ID =
            new ResourceLocation(Z80ZHealthBar.MOD_ID, "absorption");

    private AbsorptionSyncPacket() {}

    public static void handle(Player player, FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        float absorption = buf.readFloat();
        if (player != null && player.level().getEntity(entityId) instanceof LivingEntity entity) {
            entity.setAbsorptionAmount(absorption);
        }
    }

    public static void sync(LivingEntity entity, float absorption) {
        if (entity.level().isClientSide()) return;
        for (var player : entity.level().players()) {
            if (player instanceof ServerPlayer sp && player.distanceToSqr(entity) < 64 * 64) {
                NetworkHandler.sendToPlayer(sp, ID, b -> {
                    b.writeInt(entity.getId());
                    b.writeFloat(absorption);
                });
            }
        }
    }
}
