package com.z80z99.z80zhealthbar.mixin;

import com.z80z99.z80zhealthbar.overlay.HudRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric 端禁用原版覆盖层（按 HudRenderer.shouldCancelVanilla 统一裁决）。Forge 端走 RenderGuiOverlayEvent。 */
@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void onRenderPlayerHealth(GuiGraphics graphics, CallbackInfo ci) {
        // 原版 renderPlayerHealth 同时绘制生命+饥饿+护甲+氧气；CUSTOM 下任一组件启用即屏蔽本方法，
        // 由 CustomHudRenderer 自行绘制各项（避免原版子项与自定义重叠）
        if (HudRenderer.shouldCancelVanilla(HudRenderer.VanillaOverlayId.PLAYER_HEALTH)
                || HudRenderer.shouldCancelVanilla(HudRenderer.VanillaOverlayId.FOOD_LEVEL)
                || HudRenderer.shouldCancelVanilla(HudRenderer.VanillaOverlayId.AIR_LEVEL)
                || HudRenderer.shouldCancelVanilla(HudRenderer.VanillaOverlayId.ARMOR_LEVEL)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void onRenderVehicleHealth(GuiGraphics graphics, CallbackInfo ci) {
        if (HudRenderer.shouldCancelVanilla(HudRenderer.VanillaOverlayId.MOUNT_HEALTH)) ci.cancel();
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void onRenderExperienceBar(GuiGraphics graphics, int xPos, CallbackInfo ci) {
        if (HudRenderer.shouldCancelVanilla(HudRenderer.VanillaOverlayId.EXPERIENCE_BAR)) ci.cancel();
    }

    /** hotbar 组件接管（CUSTOM 模式）:取消原版物品栏,由 hotbar 组件重绘（仅 Forge 端接管生效;两端渲染入口一致） */
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void onRenderHotbar(float partialTick, GuiGraphics graphics, CallbackInfo ci) {
        if (HudRenderer.shouldCancelVanilla(HudRenderer.VanillaOverlayId.HOTBAR)) ci.cancel();
    }
}
