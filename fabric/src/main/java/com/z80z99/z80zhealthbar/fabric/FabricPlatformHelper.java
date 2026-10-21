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
    public java.util.List<com.z80z99.z80zhealthbar.platform.ModEntry> installedMods() {
        var out = new java.util.ArrayList<com.z80z99.z80zhealthbar.platform.ModEntry>();
        try {
            for (var container : FabricLoader.getInstance().getAllMods()) {
                var meta = container.getMetadata();
                out.add(new com.z80z99.z80zhealthbar.platform.ModEntry(meta.getId(), meta.getName()));
            }
        } catch (Throwable ignored) {
        }
        out.sort(java.util.Comparator.comparing(
                com.z80z99.z80zhealthbar.platform.ModEntry::displayName,
                String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Override
    public Path modJarPath(String modId) {
        try {
            var container = FabricLoader.getInstance().getModContainer(modId);
            if (container.isEmpty()) return null;
            for (Path p : container.get().getOrigin().getPaths()) {
                if (p != null && java.nio.file.Files.isRegularFile(p)) return p;
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public ClassLoader modClassLoader(String modId) {
        // Fabric 0.15 的 ModContainer 不暴露类加载器:所有模组共享 Knot 类加载器,
        // 线程上下文加载器即它（模组类由同一加载器解析）
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) return cl;
        try {
            return FabricLoader.class.getClassLoader();
        } catch (Throwable ignored) {
            return null;
        }
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
