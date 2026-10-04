package com.z80z99.z80zhealthbar.overlay.parts.compat;

import com.z80z99.z80zhealthbar.platform.PlatformService;
import com.z80z99.z80zhealthbar.overlay.BaseOverlay;
import com.z80z99.z80zhealthbar.overlay.RenderGui;
import net.minecraft.client.gui.GuiGraphics;

/** 模组联动覆盖层基类 - 自动检查 mod 是否加载 */
public abstract class CompatOverlay extends BaseOverlay {

    private final String targetModId;

    protected CompatOverlay(String targetModId) {
        this.targetModId = targetModId;
    }

    @Override
    public boolean shouldOverride() {
        return PlatformService.get().isModLoaded(targetModId)
                && com.z80z99.z80zhealthbar.config.ConfigManager.getConfig().compat.isHookEnabled(targetModId);
    }

    /**
     * 重写 render()：目标 mod 未加载时跳过渲染。
     * BaseOverlay.render() 不检查 shouldOverride()，compat 覆盖层注册到 OverlayManager 后
     * 会被 MainOverlay 无条件调用。此方法在调用 renderOverlay 前 gate 一下。
     */
    @Override
    public void render(RenderGui renderGui, GuiGraphics graphics, float partialTick,
                       int screenWidth, int screenHeight) {
        if (!shouldOverride()) return;
        super.render(renderGui, graphics, partialTick, screenWidth, screenHeight);
    }

    public String getTargetModId() {
        return targetModId;
    }
}
