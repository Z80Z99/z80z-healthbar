package com.z80z99.z80zhealthbar.fabric;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.key.KeyBindings;
import com.z80z99.z80zhealthbar.overlay.HudRenderer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

public final class FabricEventHandlers {
    /** 帧计数:接管确保逻辑按 ~10 帧一次(约 2 tick 粒度)执行 */
    private static int fabricTickCounter;

    public static void register() {
        // 注册快捷键
        for (var key : KeyBindings.ALL) {
            KeyBindingHelper.registerKeyBinding(key);
        }

        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;

            KeyBindings.handleKeyPresses();
            // 接管组件确保逻辑改为每 tick(20/s)而非每帧——与 Forge 端语义一致(审计 #15)
            if (fabricTickCounter++ % 10 == 0) {
                com.z80z99.z80zhealthbar.compat.CompatHudTakeover.tickEnsure();
            }
            HudRenderer.onPreRender(mc.gui);
            HudRenderer.render(graphics, tickDelta);
            // 数据源后台探测面板（无界面时绘制;渲染路径零反射）
            com.z80z99.z80zhealthbar.datasource.ProbeOverlay.render(graphics);
        });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 会话未激活时仅一次布尔判断
            com.z80z99.z80zhealthbar.datasource.ProbeOverlay.tick();
            // 按窗口状态自动切换用户样式
            com.z80z99.z80zhealthbar.overlay.StyleAutoSwitcher.tick();
        });

        Z80ZHealthBar.LOGGER.info("Fabric event handlers registered");
    }
}
