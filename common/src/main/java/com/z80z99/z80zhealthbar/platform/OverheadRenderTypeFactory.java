package com.z80z99.z80zhealthbar.platform;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 生物头顶血条自定义 RenderType 的跨版本工厂。
 *
 * <p>V1 (1.20.1): {@link #barRect()} 直接复用 vanilla {@code RenderType.create}。
 * <p>V1.5 (1.21.x): 1.20.5/1.21.2 RenderType 大改后，只需在平台层替换本方法实现，
 * common 调用方 {@code OverheadBarRenderer} 无须改动。
 *
 * <p>实例由各平台启动时通过 {@link Holder#set(OverheadRenderTypeFactory)} 注入。
 */
public interface OverheadRenderTypeFactory {

    /** 创建生物头顶血条用的 RenderType（QUADS + POSITION_COLOR + 半透明 + 无深度测试）。 */
    RenderType barRect();

    /** 创建铭牌图标用的 RenderType（如 icons.png 切图）。 */
    RenderType plaqueIcon(ResourceLocation texture);

    /** 创建可染色贴图矩形用的 RenderType（QUADS + POSITION_TEX_COLOR，样式 A 填充条着色）。 */
    default RenderType tintedIcon(net.minecraft.resources.ResourceLocation texture) {
        return plaqueIcon(texture);
    }

    /**
     * 调试：AsteorBar 原版复刻渲染类型 —— POSITION_COLOR_TEX_LIGHTMAP，
     * 采样 lightmap 贴图实现填充渐变（顶点顺序 color→uv→uv2，与原版逐行一致）。
     * lightmap 贴图仅由本地开发资源包提供（ARR，不入库/JAR）。
     */
    default RenderType originalBar(net.minecraft.resources.ResourceLocation lightmapTexture) {
        return barRect();
    }

    /** 平台实现注入点。沿用 PlatformService 模式：平台启动期一次性 set，common 调用方 get() 取实例。 */
    final class Holder {
        private static OverheadRenderTypeFactory factory;

        private Holder() {}

        public static OverheadRenderTypeFactory get() {
            if (factory == null) {
                throw new IllegalStateException(
                        "OverheadRenderTypeFactory not set; call Holder.set() in platform init");
            }
            return factory;
        }

        public static void set(OverheadRenderTypeFactory factory) {
            if (Holder.factory != null) {
                throw new IllegalStateException("OverheadRenderTypeFactory already set");
            }
            Holder.factory = factory;
        }
    }
}