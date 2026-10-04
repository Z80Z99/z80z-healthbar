package com.z80z99.z80zhealthbar.mobdisplay;

import com.z80z99.z80zhealthbar.platform.OverheadRenderTypeFactory;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 静态访问入口，向后兼容 mobdisplay 包内 5 个调用方。
 * 实际 RenderType 创建委托到 {@link OverheadRenderTypeFactory.Holder}，
 * 由各平台启动期通过 {@link OverheadRenderTypeFactoryV1} 注入。
 * V1.5 (1.21.x) 移植时只替换工厂实现，调用方与 ModRenderType 本身不变。
 */
public final class ModRenderType {
    private ModRenderType() {}

    public static RenderType tintedIcon(ResourceLocation texture) {
        return OverheadRenderTypeFactory.Holder.get().tintedIcon(texture);
    }

    /** 调试：AsteorBar 原版复刻渲染类型（lightmap 渐变管线） */
    public static RenderType originalBar(ResourceLocation lightmapTexture) {
        return OverheadRenderTypeFactory.Holder.get().originalBar(lightmapTexture);
    }

    public static RenderType plaqueIcon(ResourceLocation texture) {
        return OverheadRenderTypeFactory.Holder.get().plaqueIcon(texture);
    }

    public static RenderType barRect() {
        return OverheadRenderTypeFactory.Holder.get().barRect();
    }
}
