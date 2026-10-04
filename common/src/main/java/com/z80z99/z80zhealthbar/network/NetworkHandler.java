package com.z80z99.z80zhealthbar.network;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.network.packets.AbsorptionSyncPacket;
import com.z80z99.z80zhealthbar.network.packets.DamagePopupPacket;
import com.z80z99.z80zhealthbar.network.packets.ExhaustionSyncPacket;
import com.z80z99.z80zhealthbar.network.packets.SaturationSyncPacket;
import com.z80z99.z80zhealthbar.platform.PlatformService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 统一网络层。数据包按 ResourceLocation 区分，编码由包类自身提供，
 * 传输经 IPlatformHelper 平台实现（Forge SimpleChannel / Fabric 原生 networking），
 * 不依赖 Architectury API（任务书 8 节）。
 */
public final class NetworkHandler {

    public static final ResourceLocation CHANNEL_ID =
            new ResourceLocation(Z80ZHealthBar.MOD_ID, "sync");

    private NetworkHandler() {}

    public static void init() {
        registerReceiver(AbsorptionSyncPacket.ID, AbsorptionSyncPacket::handle);
        registerReceiver(SaturationSyncPacket.ID, SaturationSyncPacket::handle);
        registerReceiver(ExhaustionSyncPacket.ID, ExhaustionSyncPacket::handle);
        registerReceiver(DamagePopupPacket.ID, DamagePopupPacket::handle);
        Z80ZHealthBar.LOGGER.info("NetworkHandler initialized with 4 sync packets");
    }

    /** 客户端注册接收器（服务端环境为 no-op；平台未注入时静默跳过，如 Fabric 专用服务器） */
    public static void registerReceiver(ResourceLocation packetId,
                                        BiConsumer<Player, FriendlyByteBuf> handler) {
        if (!PlatformService.has()) return;
        PlatformService.get().registerClientReceiver(packetId, handler);
    }

    /** 服务端发送自定义包（平台未注入时静默跳过） */
    public static void sendToPlayer(ServerPlayer player, ResourceLocation packetId,
                                    Consumer<FriendlyByteBuf> writer) {
        if (!PlatformService.has()) return;
        PlatformService.get().sendToPlayer(player, packetId, writer);
    }
}
