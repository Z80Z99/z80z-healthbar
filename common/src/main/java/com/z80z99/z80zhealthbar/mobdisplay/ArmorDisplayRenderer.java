package com.z80z99.z80zhealthbar.mobdisplay;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.status.EntityStatusSnapshot;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** 护甲渲染器：条形（附加行）+ 原版护甲图标牌匾行 */
public class ArmorDisplayRenderer implements IMobDisplayRenderer {
    private static final ResourceLocation KEY = new ResourceLocation(Z80ZHealthBar.MOD_ID, "armor");
    private static final ResourceLocation ICONS =
            new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final int MAX_SLOTS = 10;

    @Override public ResourceLocation getKey() { return KEY; }

    @Override
    public boolean wantsToRender(EntityStatusSnapshot snap) {
        return snap.hasArmor() && snap.armor > 0;
    }

    @Override
    public void renderBar(PoseStack poseStack, MultiBufferSource buffer, Font font,
                          EntityStatusSnapshot snap, int x, int y, int barWidth, float alpha, float worldScale) {
        var colors = ConfigManager.getConfig().colors;
        int barH = ConfigManager.getConfig().barStyle.barHalfHeight * 2;
        int fillColor = ColorHelper.modifyAlpha(ColorHelper.parseColor(colors.armor), (int) (alpha * 255));
        int emptyColor = ColorHelper.modifyAlpha(ColorHelper.parseColor(colors.armorEmpty), (int) (alpha * 255));
        int boundColor = ColorHelper.modifyAlpha(ColorHelper.parseColor(colors.armorBound), (int) (alpha * 255));
        float zu = 1f / Math.max(1e-5f, worldScale);

        Matrix4f matrix = poseStack.last().pose();
        drawRect(matrix, buffer, x, y, barWidth, barH, emptyColor);

        int fillW = (int) (snap.armor / 20.0 * barWidth);
        fillW = Math.max(0, Math.min(barWidth, fillW));
        if (fillW > 0) {
            poseStack.pushPose();
            poseStack.translate(0, 0, -0.02f * zu);
            drawRect(poseStack.last().pose(), buffer, x, y, fillW, barH, fillColor);
            poseStack.popPose();
        }

        poseStack.pushPose();
        poseStack.translate(0, 0, -0.04f * zu);
        Matrix4f mBound = poseStack.last().pose();
        drawRect(mBound, buffer, x, y, barWidth, 1, boundColor);
        drawRect(mBound, buffer, x, y + barH - 1, barWidth, 1, boundColor);
        drawRect(mBound, buffer, x, y, 1, barH, boundColor);
        drawRect(mBound, buffer, x + barWidth - 1, y, 1, barH, boundColor);
        poseStack.popPose();
    }

    @Override
    public void renderPlaque(PoseStack poseStack, MultiBufferSource buffer,
                              EntityStatusSnapshot snap, int x, int y,
                              Font font, int packedLight, float alpha, float worldScale) {
        int curSlots = (int) Math.min(MAX_SLOTS, Math.ceil(snap.armor / 2.0));

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer builder = buffer.getBuffer(ModRenderType.plaqueIcon(ICONS));
        float zu = 1f / Math.max(1e-5f, worldScale);

        // container(空) armor (16,9)；filled armor (34,9) —— 填充层向相机 -0.02 世界格防排序翻转闪烁
        for (int i = 0; i < MAX_SLOTS; i++) {
            icon(builder, matrix, x + i * 9, y, 16, 9);
        }
        if (curSlots > 0) {
            poseStack.pushPose();
            poseStack.translate(0, 0, -0.02f * zu);
            Matrix4f mFill = poseStack.last().pose();
            for (int i = 0; i < curSlots; i++) {
                icon(builder, mFill, x + i * 9, y, 34, 9);
            }
            poseStack.popPose();
        }
    }

    /** 9x9 图标（标准顶点顺序，与原版 blit/原 Mob Plaques innerBlit 一致，勿交换 u） */
    static void icon(VertexConsumer builder, Matrix4f m, int x, int y, int u, int v) {
        float u1 = u / 256f, v1 = v / 256f, u2 = (u + 9) / 256f, v2 = (v + 9) / 256f;
        builder.vertex(m, x, y, 0).uv(u1, v1).endVertex();
        builder.vertex(m, x, y + 9, 0).uv(u1, v2).endVertex();
        builder.vertex(m, x + 9, y + 9, 0).uv(u2, v2).endVertex();
        builder.vertex(m, x + 9, y, 0).uv(u2, v1).endVertex();
    }

    /** 9x9 染色图标（POSITION_TEX_COLOR 管线），用于与同 UV 的护甲图标区分（韧性） */
    static void iconTinted(VertexConsumer builder, Matrix4f m, int x, int y, int u, int v, int color) {
        float u1 = u / 256f, v1 = v / 256f, u2 = (u + 9) / 256f, v2 = (v + 9) / 256f;
        builder.vertex(m, x, y, 0).uv(u1, v1).color(color).endVertex();
        builder.vertex(m, x, y + 9, 0).uv(u1, v2).color(color).endVertex();
        builder.vertex(m, x + 9, y + 9, 0).uv(u2, v2).color(color).endVertex();
        builder.vertex(m, x + 9, y, 0).uv(u2, v1).color(color).endVertex();
    }

    @Override public int getBarWidth(EntityStatusSnapshot snap) {
        return Math.min(80, Math.max(20, (int) (snap.armor * 2.0)));
    }

    @Override public int getPlaqueWidth(Font font, EntityStatusSnapshot snap) { return MAX_SLOTS * 9; }

    @Override
    public String getValueText(EntityStatusSnapshot snap) { return String.valueOf(snap.armor); }

    private void drawRect(Matrix4f m, MultiBufferSource b, int x, int y, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        VertexConsumer v = b.getBuffer(ModRenderType.barRect());
        v.vertex(m, x, y, 0).color(c).endVertex();
        v.vertex(m, x, y + h, 0).color(c).endVertex();
        v.vertex(m, x + w, y + h, 0).color(c).endVertex();
        v.vertex(m, x + w, y, 0).color(c).endVertex();
    }
}
