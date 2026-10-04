package com.z80z99.z80zhealthbar.network.packets;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** S2C: 同步玩家疲劳度 */
public final class ExhaustionSyncPacket {
    public static final ResourceLocation ID =
            new ResourceLocation(Z80ZHealthBar.MOD_ID, "exhaustion");

    private ExhaustionSyncPacket() {}

    public static void handle(Player player, FriendlyByteBuf buf) {
        float exhaustion = buf.readFloat();
        if (player != null) {
            player.getFoodData().setExhaustion(exhaustion);
        }
    }

    public static void sync(ServerPlayer player, float exhaustion) {
        NetworkHandler.sendToPlayer(player, ID, b -> b.writeFloat(exhaustion));
    }
}
