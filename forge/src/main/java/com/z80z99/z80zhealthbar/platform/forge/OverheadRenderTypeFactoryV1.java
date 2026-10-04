package com.z80z99.z80zhealthbar.platform.forge;

import com.z80z99.z80zhealthbar.platform.OverheadRenderTypeFactory;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * V1 (1.20.1) Forge 版 RenderType 工厂。
 * Forge 补丁 jar 已将 RenderType.create(String,VertexFormat,Mode,int,boolean,boolean,CompositeState)
 * 放开为 public（Forge 官方 Access Transformer），因此 Forge 模块可直接调用。
 * Fabric 侧的对应实现位于 fabric 模块（用 access widener 放开同方法）。
 */
public final class OverheadRenderTypeFactoryV1 extends RenderType implements OverheadRenderTypeFactory {

    private static final RenderType BAR_RECT = RenderType.create("z80zhealthbar_bar",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false, // sortOnUpload=false:同心多层按插入序绘制,距离排序在同中心四边形间不稳定(层叠翻转闪烁根源)
            CompositeState.builder()
                    .setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    // 屏显语义:无深度测试+不写深度——血条不受地形/结构/身体遮挡,永不污染深度缓冲
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    @SuppressWarnings("unused") // 仅为访问 protected 成员而继承
    public OverheadRenderTypeFactoryV1() {
        super("z80zhealthbar_factory", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS,
                0, false, false, () -> {}, () -> {});
    }

    @Override
    public RenderType barRect() {
        return BAR_RECT;
    }

    // 渲染类型按贴图缓存:RenderType 实例是 BufferSource 的批次键,每次 new 会产生
    // 独立 BufferBuilder + 独立绘制调用(每实体每帧数次 → 数十上百次/帧,实体移动时爆量)。
    // 缓存后全帧同类型几何合并为单个批次,绘制调用坍缩到个位数
    private static final java.util.concurrent.ConcurrentHashMap<ResourceLocation, RenderType> PLAIN_ICONS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<ResourceLocation, RenderType> TINTED_ICONS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<ResourceLocation, RenderType> ORIGINAL_BARS =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public RenderType plaqueIcon(ResourceLocation texture) {
        return PLAIN_ICONS.computeIfAbsent(texture, tex -> RenderType.create(
                "z80zhealthbar_icon_" + tex.getPath(),
                DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256, false, false, // sortOnUpload=false:同心多层按插入序绘制,距离排序在同中心四边形间不稳定(层叠翻转闪烁根源)
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexShader))
                        .setTextureState(new TextureStateShard(tex, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }

    @Override
    public RenderType tintedIcon(ResourceLocation texture) {
        return TINTED_ICONS.computeIfAbsent(texture, tex -> RenderType.create(
                "z80zhealthbar_icon_tinted_" + tex.getPath(),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false, // sortOnUpload=false
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new TextureStateShard(tex, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }

    @Override
    public RenderType originalBar(ResourceLocation lightmapTexture) {
        // AsteorBar 原版复刻：POSITION_COLOR_TEX_LIGHTMAP + 渐变采样（见 AsteorBarRenderType 反编译）
        return ORIGINAL_BARS.computeIfAbsent(lightmapTexture, tex -> RenderType.create(
                "z80zhealthbar_original_bar",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 131072, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getPositionColorTexLightmapShader))
                        .setTextureState(new TextureStateShard(tex, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(LIGHTMAP)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .createCompositeState(false)));
    }
}
