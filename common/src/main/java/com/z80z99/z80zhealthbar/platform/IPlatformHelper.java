package com.z80z99.z80zhealthbar.platform;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 平台抽象层。common 模块只通过本接口访问加载器特定能力，
 * 不直接依赖 Forge/Fabric/Architectury API（见 docs/architecture.md 分层规则）。
 */
public interface IPlatformHelper {

    Path getConfigDir();

    boolean isClientSide();

    boolean isModLoaded(String modId);

    String getModVersion(String modId);

    /**
     * 全部已安装模组（id + 展示名,按展示名排序）。自定义数据源的目标模组选择列表用。
     * 服务端/非客户端环境返回空列表。
     */
    java.util.List<ModEntry> installedMods();

    /**
     * 目标模组的 jar 文件路径（数据源扫描器枚举 class 条目用）。
     * 模组不存在、以目录形式加载、或加载器不提供时返回 null。
     */
    Path modJarPath(String modId);

    /**
     * 目标模组的类加载器（扫描器加载目标模组类用）。
     * 取不到时由调用方回退到线程上下文类加载器。
     */
    ClassLoader modClassLoader(String modId);

    /**
     * 服务端发送一个自定义 S2C 包到指定玩家。
     * 由平台实现负责封包（Forge SimpleChannel / Fabric ServerPlayNetworking）。
     */
    void sendToPlayer(ServerPlayer player, ResourceLocation packetId, Consumer<FriendlyByteBuf> writer);

    /**
     * 注册客户端接收器；平台实现须保证 handler 在主线程执行。
     * 服务端环境调用时应安全 no-op。
     */
    void registerClientReceiver(ResourceLocation packetId, BiConsumer<Player, FriendlyByteBuf> handler);
}
