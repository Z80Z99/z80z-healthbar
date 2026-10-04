package com.z80z99.z80zhealthbar.forge;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.platform.IPlatformHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import io.netty.buffer.Unpooled;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ForgePlatformHelper implements IPlatformHelper {

    /** 单一信封通道：所有自定义包复用一个 SimpleChannel 消息，按 ResourceLocation 分发 */
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Z80ZHealthBar.MOD_ID, "main"),
            () -> "1", "1"::equals, "1"::equals);

    private static final Map<ResourceLocation, BiConsumer<Player, FriendlyByteBuf>> CLIENT_RECEIVERS = new HashMap<>();

    static {
        CHANNEL.messageBuilder(Envelope.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(Envelope::decode)
                .encoder(Envelope::encode)
                .consumerMainThread(ForgePlatformHelper::handleEnvelope)
                .add();
    }

    private record Envelope(ResourceLocation id, byte[] data) {
        static Envelope decode(FriendlyByteBuf buf) {
            return new Envelope(buf.readResourceLocation(), buf.readByteArray());
        }
        void encode(FriendlyByteBuf buf) {
            buf.writeResourceLocation(id);
            buf.writeByteArray(data);
        }
    }

    /** consumerMainThread 保证主线程；此处仅客户端会收到 */
    private static void handleEnvelope(Envelope envelope, Supplier<NetworkEvent.Context> ctx) {
        BiConsumer<Player, FriendlyByteBuf> handler = CLIENT_RECEIVERS.get(envelope.id());
        if (handler == null) return;
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        handler.accept(player, new FriendlyByteBuf(Unpooled.wrappedBuffer(envelope.data())));
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isClientSide() {
        return net.minecraftforge.api.distmarker.Dist.CLIENT.isClient();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public String getModVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ResourceLocation packetId,
                             Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(buf);
        // 注意：负载无长度前缀，须按"剩余可读字节"整体提取；
        // readByteArray() 会先读 varint 长度前缀，会把负载误当长度解析
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Envelope(packetId, data));
    }

    @Override
    public void registerClientReceiver(ResourceLocation packetId,
                                       BiConsumer<Player, FriendlyByteBuf> handler) {
        CLIENT_RECEIVERS.put(packetId, handler);
    }
}
