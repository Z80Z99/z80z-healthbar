package com.z80z99.z80zhealthbar.forge;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.key.KeyBindings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
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
            // 接管开关打开且自定义 HUD 时,确保对应数据组件存在（一次性;MOD 未装时等待）
            com.z80z99.z80zhealthbar.compat.CompatHudTakeover.tickEnsure();
        }
    }

    /**
     * 接管第三方 HUD（用户需求"检测到 mod 则顶掉它的 HUD"）：
     * RenderGuiOverlayEvent.Pre 可取消——取消后 ForgeGui 跳过该 overlay 的渲染
     * （同时其 Post 也不会触发,对方挂在同 id 上的附加绘制一并消失）。
     * 仅当 CompatHudTakeover 判定"我方确实会绘制该数据"时才取消（三重安全网）。
     */
    @SubscribeEvent
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        var id = event.getOverlay().id();
        if (com.z80z99.z80zhealthbar.compat.CompatHudTakeover.shouldCancelOverlay(
                id.getNamespace(), id.getPath())) {
            event.setCanceled(true);
            var adapter = com.z80z99.z80zhealthbar.compat.CompatHudTakeover
                    .adapterForOverlay(id.getNamespace(), id.getPath());
            if (adapter != null) {
                com.z80z99.z80zhealthbar.compat.CompatHudTakeover.logCancelOnce(adapter);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        MobDisplayRenderer.replayWorldOverlays();
    }
}
