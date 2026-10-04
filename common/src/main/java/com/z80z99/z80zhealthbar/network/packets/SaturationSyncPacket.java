package com.z80z99.z80zhealthbar.network.packets;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** S2C: 同步玩家饱和度，使客户端 HUD 能准确显示 */
public final class SaturationSyncPacket {
    public static final ResourceLocation ID =
            new ResourceLocation(Z80ZHealthBar.MOD_ID, "saturation");

    private SaturationSyncPacket() {}

    public static void handle(Player player, FriendlyByteBuf buf) {
        float saturation = buf.readFloat();
        if (player != null) {
            player.getFoodData().setSaturation(saturation);
        }
    }

    public static void sync(ServerPlayer player, float saturation) {
        NetworkHandler.sendToPlayer(player, ID, b -> b.writeFloat(saturation));
    }
}
