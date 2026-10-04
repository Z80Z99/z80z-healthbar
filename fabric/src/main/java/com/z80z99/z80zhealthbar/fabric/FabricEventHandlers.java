package com.z80z99.z80zhealthbar.fabric;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.key.KeyBindings;
import com.z80z99.z80zhealthbar.overlay.HudRenderer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

public final class FabricEventHandlers {
    public static void register() {
        // 注册快捷键
        for (var key : KeyBindings.ALL) {
            KeyBindingHelper.registerKeyBinding(key);
        }

        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;

            KeyBindings.handleKeyPresses();
            HudRenderer.onPreRender(mc.gui);
            HudRenderer.render(graphics, tickDelta);
        });

        Z80ZHealthBar.LOGGER.info("Fabric event handlers registered");
    }
}
