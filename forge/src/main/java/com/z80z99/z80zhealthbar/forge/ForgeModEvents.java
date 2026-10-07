package com.z80z99.z80zhealthbar.forge;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.key.KeyBindings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.z80z99.z80zhealthbar.mobdisplay.MobDisplayRenderer;

@Mod.EventBusSubscriber(modid = Z80ZHealthBar.MOD_ID, value = Dist.CLIENT)
public final class ForgeModEvents {

    static {
        // 世界覆盖层（血条/跳字）改为 GUI 通道末尾重放:世界通道 TAIL 的像素在
        // "相机→生物之间隔着玻璃+水体"时会被吃掉（实测）,而 GUI 通道稳定可见
        MobDisplayRenderer.setOverlayDeferred(true);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            KeyBindings.handleKeyPresses();
        }
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        MobDisplayRenderer.replayWorldOverlays();
    }
}
