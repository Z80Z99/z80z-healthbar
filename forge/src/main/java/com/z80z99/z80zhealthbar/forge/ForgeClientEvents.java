package com.z80z99.z80zhealthbar.forge;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.key.KeyBindings;
import com.z80z99.z80zhealthbar.overlay.HudRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class ForgeClientEvents {

    /** MOD 总线事件 - 注册快捷键 */
    @Mod.EventBusSubscriber(modid = Z80ZHealthBar.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            for (var key : KeyBindings.ALL) {
                event.register(key);
            }
        }
    }

    /** FORGE 总线事件 - HUD 渲染 */
    @Mod.EventBusSubscriber(modid = Z80ZHealthBar.MOD_ID, value = Dist.CLIENT)
    public static final class Forge {
        @SubscribeEvent
        public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
            var overlay = event.getOverlay();

            // 按当前 HUD 样式逐项决定是否屏蔽原版覆盖层（VANILLA 模式全部放行）
            if (overlay == VanillaGuiOverlay.PLAYER_HEALTH.type()) {
                if (HudRenderer.shouldCancelVanilla(HudRenderer.VanillaOverlayId.PLAYER_HEALTH)) {
                    event.setCanceled(true);
                }
            } else if (overlay == VanillaGuiOverlay.FOOD_LEVEL.type()) {
                if (HudRenderer.shouldCancelVanilla(HudRenderer.VanillaOverlayId.FOOD_LEVEL)) {
                    event.setCanceled(true);
                }
            } else if (overlay == VanillaGuiOverlay.AIR_LEVEL.type()) {
                if (HudRenderer.shouldCancelVanilla(HudRenderer.VanillaOverlayId.AIR_LEVEL)) {
                    event.setCanceled(true);
                }
            } else if (overlay == VanillaGuiOverlay.EXPERIENCE_BAR.type()) {
                if (HudRenderer.shouldCancelVanilla(HudRenderer.VanillaOverlayId.EXPERIENCE_BAR)) {
                    event.setCanceled(true);
                }
            } else if (overlay == VanillaGuiOverlay.ARMOR_LEVEL.type()) {
                if (HudRenderer.shouldCancelVanilla(HudRenderer.VanillaOverlayId.ARMOR_LEVEL)) {
                    event.setCanceled(true);
                }
            } else if (overlay == VanillaGuiOverlay.MOUNT_HEALTH.type()) {
                if (HudRenderer.shouldCancelVanilla(HudRenderer.VanillaOverlayId.MOUNT_HEALTH)) {
                    event.setCanceled(true);
                }
            } else if (overlay == VanillaGuiOverlay.HOTBAR.type()) {
                // hotbar 组件接管（CUSTOM 模式 9 实例非 OFF）:取消原版物品栏,由 hotbar 组件重绘
                if (HudRenderer.shouldCancelVanilla(HudRenderer.VanillaOverlayId.HOTBAR)) {
                    event.setCanceled(true);
                }
            }

            if (overlay == VanillaGuiOverlay.VIGNETTE.type()) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                HudRenderer.onPreRender(mc.gui);
            }
        }

        @SubscribeEvent
        public static void onRenderGuiOverlayPost(RenderGuiOverlayEvent.Post event) {
            // HUD 入口原挂 HOTBAR Post;hotbar 组件接管时该 overlay 的 Pre 会被取消（Post 同灭）,
            // 改挂 VIGNETTE Post（事件链最先、永不被本模组取消,时序在原版 hotbar 之后——
            // hotbar 接管重绘发生在其自身分支内,晚于 VIGNETTE,不会闪烁）
            if (event.getOverlay() == VanillaGuiOverlay.VIGNETTE.type()) {
                HudRenderer.render(event.getGuiGraphics(), event.getPartialTick());
                // 数据源后台探测面板（无界面时绘制;渲染路径零反射）
                com.z80z99.z80zhealthbar.datasource.ProbeOverlay.render(event.getGuiGraphics());
                // 自定义聊天窗口（1.0.150;打开聊天时附加交互装饰;Post 事件无鼠标→实时读 MouseHandler）
                var mcChat = net.minecraft.client.Minecraft.getInstance();
                double cmx = mcChat.mouseHandler.xpos() * (double) mcChat.getWindow().getGuiScaledWidth()
                        / mcChat.getWindow().getWidth();
                double cmy = mcChat.mouseHandler.ypos() * (double) mcChat.getWindow().getGuiScaledHeight()
                        / mcChat.getWindow().getHeight();
                com.z80z99.z80zhealthbar.chat.ChatWindowRenderer.render(event.getGuiGraphics(),
                        (int) cmx, (int) cmy,
                        mcChat.screen instanceof com.z80z99.z80zhealthbar.gui.ChatWindowScreen);
            }
        }

        /** 客户端 tick:驱动数据源探测采样（会话未激活时仅一次布尔判断） */
        @SubscribeEvent
        public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
            if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
            com.z80z99.z80zhealthbar.datasource.ProbeOverlay.tick();
            // 按窗口状态自动切换用户样式（会话未激活时仅几次布尔判断）
            com.z80z99.z80zhealthbar.overlay.StyleAutoSwitcher.tick();
        }
    }
}
