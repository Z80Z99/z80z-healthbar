package com.z80z99.z80zhealthbar.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class GuiHelper {
    private GuiHelper() {}

    /** 绑定纹理到 0 号采样槽 — drawTexturedRect 系列不自动绑定，绘制前必须调用 */
    public static void bindTexture(ResourceLocation texture) {
        RenderSystem.setShaderTexture(0, texture);
    }

    /** 绑定纹理并绘制矩形（图标切图等） */
    public static void drawTexturedRect(ResourceLocation texture, GuiGraphics graphics,
                                        int x, int y, int u, int v, int w, int h) {
        bindTexture(texture);
        drawTexturedRect(graphics, x, y, u, v, w, h);
    }

    /** 绑定纹理并绘制自定义 UV 矩形（支持拉伸/任意尺寸） */
    public static void drawTexturedRect(ResourceLocation texture, GuiGraphics graphics,
                                        int x1, int y1, int x2, int y2,
                                        float u1, float v1, float u2, float v2,
                                        int texWidth, int texHeight) {
        bindTexture(texture);
        drawTexturedRect(graphics, x1, y1, x2, y2, u1, v1, u2, v2, texWidth, texHeight);
    }

    /** 绑定纹理并绘制染色矩形（图标染色，如坐骑橙色心形） */
    public static void drawTexturedRectColor(ResourceLocation texture, GuiGraphics graphics,
                                             int x, int y, int u, int v, int w, int h, int color) {
        bindTexture(texture);
        drawTexturedRectColor(graphics, x, y, x + w, y + h, u, v, u + w, v + h, 256, 256, color);
    }

    public static void drawTexturedRect(GuiGraphics graphics, int x, int y, int u, int v, int w, int h) {
        drawTexturedRect(graphics, x, y, x + w, y + h, u, v, u + w, v + h, 256, 256);
    }

    public static void drawTexturedRect(GuiGraphics graphics,
                                         int x1, int y1, int x2, int y2,
                                         float u1, float v1, float u2, float v2,
                                         int texWidth, int texHeight) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        Matrix4f matrix = graphics.pose().last().pose();

        builder.vertex(matrix, (float)x1, (float)y1, 0f).uv(u1 / texWidth, v1 / texHeight).endVertex();
        builder.vertex(matrix, (float)x1, (float)y2, 0f).uv(u1 / texWidth, v2 / texHeight).endVertex();
        builder.vertex(matrix, (float)x2, (float)y2, 0f).uv(u2 / texWidth, v2 / texHeight).endVertex();
        builder.vertex(matrix, (float)x2, (float)y1, 0f).uv(u2 / texWidth, v1 / texHeight).endVertex();

        BufferUploader.drawWithShader(builder.end());
        RenderSystem.disableBlend();
    }

    public static void drawTexturedRectColor(GuiGraphics graphics,
                                              int x1, int y1, int x2, int y2,
                                              float u1, float v1, float u2, float v2,
                                              int texWidth, int texHeight, int color) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();

        // 顶点元素顺序必须与格式 POSITION_TEX_COLOR 一致：position → uv → color，
        // 否则 endVertex() 抛 "Not filled all elements of the vertex"（崩溃根因，Wave 12 修复）
        builder.vertex(matrix, (float)x1, (float)y1, 0f).uv(u1 / texWidth, v1 / texHeight).color(color).endVertex();
        builder.vertex(matrix, (float)x1, (float)y2, 0f).uv(u1 / texWidth, v2 / texHeight).color(color).endVertex();
        builder.vertex(matrix, (float)x2, (float)y2, 0f).uv(u2 / texWidth, v2 / texHeight).color(color).endVertex();
        builder.vertex(matrix, (float)x2, (float)y1, 0f).uv(u2 / texWidth, v1 / texHeight).color(color).endVertex();

        BufferUploader.drawWithShader(builder.end());
        RenderSystem.disableBlend();
    }

    public static void drawSolidColor(GuiGraphics graphics, int x, int y, int x2, int y2, int color) {
        graphics.fill(x, y, x2, y2, color);
    }

    public static void drawString(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, text, x, y, color);
    }

    public static void drawString(GuiGraphics graphics, String text, int x, int y, int color, boolean shadow) {
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, text, x, y, color, shadow);
    }
}
