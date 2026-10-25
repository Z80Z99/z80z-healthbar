package com.z80z99.z80zhealthbar.mixin;

import com.z80z99.z80zhealthbar.chat.ChatWindowManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 聊天界面替换（1.0.150,双平台）：setScreen 收到原版 ChatScreen 且聊天窗口接管时,
 * 换成 ChatWindowScreen（继承 ChatScreen——Tab 补全/输入历史/Esc 全保留）。
 * 精确匹配 ChatScreen.class,ChatWindowScreen 自身（子类）不会被二次替换。
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen replaceChatScreen(Screen screen) {
        if (screen != null && screen.getClass() == ChatScreen.class && ChatWindowManager.takeover()) {
            String initial = "";
            try {
                var f = ChatScreen.class.getDeclaredField("initial");
                f.setAccessible(true);
                initial = (String) f.get(screen);
            } catch (Throwable ignored) {
            }
            return new com.z80z99.z80zhealthbar.gui.ChatWindowScreen(initial == null ? "" : initial);
        }
        return screen;
    }
}
