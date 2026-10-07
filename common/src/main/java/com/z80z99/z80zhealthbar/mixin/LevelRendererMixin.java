package com.z80z99.z80zhealthbar.mixin;

import com.z80z99.z80zhealthbar.mobdisplay.DamagePopupRenderer;
import com.z80z99.z80zhealthbar.mobdisplay.MobDisplayRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 世界空间覆盖层通道（血条 → 跳字），注入 LevelRenderer.renderLevel 末尾。
 *
 * <p>必须在世界通道末尾而非 GameRenderer.renderLevel 末尾绘制：后者流程为
 * 世界渲染 → clear 深度 → 第一人称手部渲染（手部把投影矩阵重置为
 * getFov(..., false) = 固定 70°，无视玩家 FOV 设置）。若挂在 GameRenderer 末尾，
 * 覆盖层会沿用手部投影：玩家 FOV ≠ 70 时血条与世界错位，且会盖在手部之上。
 * 挂 LevelRenderer 末尾时：投影仍是带 FOV 的世界投影、深度完好、手部未渲染，
 * 世界缩放/位置与实体完全一致，手部随后正常覆盖在其上（与原版名牌层级一致）。
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;"
            + "Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;"
            + "Lorg/joml/Matrix4f;)V", at = @At("TAIL"))
    private void z80z$renderWorldOverlays(PoseStack poseStack, float partialTick, long finishNanoTime,
                                          boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                          LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (!MobDisplayRenderer.barsPending() && !DamagePopupRenderer.popupsPending()) return;
        // 此刻 poseStack 即世界视图矩阵（与 GameRenderer 传入 LevelRenderer 时同一状态）。
        // 延迟模式下（Forge 注册了 GUI 通道重放）只暂存矩阵——世界通道末尾的像素会被
        // 玻璃+水体组合吃掉,GUI 通道（RenderGuiEvent.Post）用暂存矩阵重放才稳定可见
        MobDisplayRenderer.renderOrStashWorldOverlays(poseStack.last().pose());
    }
}
