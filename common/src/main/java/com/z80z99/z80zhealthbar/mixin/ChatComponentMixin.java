package com.z80z99.z80zhealthbar.mixin;

import com.z80z99.z80zhealthbar.chat.ChatWindowManager;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 聊天窗口接管（1.0.150,双平台同一路径——Forge 不再走 overlay 事件,保证行为一致）：
 * 取消原版聊天渲染;getClickedComponentStyleAt 置空防"窗口已挪走但原坐标仍可幻影点击"。
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(GuiGraphics graphics, int tickCount, int mouseX, int mouseY, CallbackInfo ci) {
        if (ChatWindowManager.takeover()) ci.cancel();
    }

    @Inject(method = "getClickedComponentStyleAt", at = @At("HEAD"), cancellable = true)
    private void onGetClickedStyle(double mx, double my, CallbackInfoReturnable<Style> cir) {
        if (ChatWindowManager.takeover()) cir.setReturnValue(null);
    }
}
