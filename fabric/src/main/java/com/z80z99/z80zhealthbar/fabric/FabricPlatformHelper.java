package com.z80z99.z80zhealthbar.fabric;

import com.z80z99.z80zhealthbar.platform.IPlatformHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isClientSide() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public String getModVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("");
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ResourceLocation packetId,
                             Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(PacketByteBufs.create());
        writer.accept(buf);
        ServerPlayNetworking.send(player, packetId, buf);
    }

    @Override
    public void registerClientReceiver(ResourceLocation packetId,
                                       BiConsumer<Player, FriendlyByteBuf> handler) {
        // 专用服务器上 ClientPlayNetworking 类不存在，环境判断先行返回，
        // 避免类加载/验证触碰纯客户端 API
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        ClientPlayNetworking.registerGlobalReceiver(packetId,
                (client, listener, buf, responseSender) -> {
                    FriendlyByteBuf copy = PacketByteBufs.copy(buf);
                    client.execute(() -> handler.accept(client.player, copy));
                });
    }
}
