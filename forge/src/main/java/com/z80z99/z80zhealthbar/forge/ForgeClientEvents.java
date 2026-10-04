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
            }

            if (overlay == VanillaGuiOverlay.VIGNETTE.type()) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                HudRenderer.onPreRender(mc.gui);
            }
        }

        @SubscribeEvent
        public static void onRenderGuiOverlayPost(RenderGuiOverlayEvent.Post event) {
            if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()) {
                HudRenderer.render(event.getGuiGraphics(), event.getPartialTick());
            }
        }
    }
}
