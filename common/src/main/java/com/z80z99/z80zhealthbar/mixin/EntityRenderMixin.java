package com.z80z99.z80zhealthbar.mixin;

import com.z80z99.z80zhealthbar.mobdisplay.MobDisplayRenderer;
import com.z80z99.z80zhealthbar.mobdisplay.MobHealthBarStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class EntityRenderMixin {

    // Wave 7 隐患记录（运行时未验证）：
    //   当前注入 TAIL 在 LivingEntityRenderer.render 末尾，与原版名称标签渲染顺序耦合。
    //   若实测出现头顶条与名称标签层级冲突，可改 @At("INVOKE", target="renderNameTag(...)", shift=At.Shift.AFTER)
    //   或注入到 EntityRenderDispatcher.render 末尾（更底层，绕过 name tag 链路）。
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"))
    private void onRenderEntity(LivingEntity entity, float yRot, float partialTick,
                                 PoseStack poseStack, MultiBufferSource buffer,
                                 int packedLight, CallbackInfo ci) {
        MobDisplayRenderer.render(entity, poseStack, buffer, partialTick, packedLight);
    }

    // 样式 A（MobHealthBar）绘制血条时接管名牌（原 MOD 在 RenderNameTagEvent 中 DENY，
    // 本项目在 shouldShowName 阶段返回 false 等效实现），避免名字双重显示。
    // 显式描述符：LivingEntityRenderer 上存在真实方法 + Entity 桥接两个重载
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void onShouldShowName(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (MobHealthBarStyle.suppressesNameTag(entity)) {
            cir.setReturnValue(false);
        }
    }
}
