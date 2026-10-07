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
        if (curSlots <= 0) return;

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer builder = buffer.getBuffer(ModRenderType.plaqueIcon(ICONS));

        // 只画实际存在的图标（filled armor (34,9)）——不再铺 10 格空槽底：
        // 空槽是一长排无意义的灰图标，且会把数值文本顶到很右边（实测"排列那么奇怪"）；
        // 与氧气行同一处理（按实际数量绘制）
        for (int i = 0; i < curSlots; i++) {
            icon(builder, matrix, x + i * 9, y, 34, 9);
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

    /** 行宽按实际图标数（与绘制一致）——固定 10 格宽会在图标与数值文本间留一大段空白 */
    @Override public int getPlaqueWidth(Font font, EntityStatusSnapshot snap) {
        int curSlots = (int) Math.min(MAX_SLOTS, Math.ceil(snap.armor / 2.0));
        return Math.max(9, curSlots * 9);
    }

    @Override
    public String getValueText(EntityStatusSnapshot snap) { return String.valueOf(snap.armor); }

    @Override
    public void renderBadge(PoseStack poseStack, MultiBufferSource buffer,
                             EntityStatusSnapshot snap, int x, int y,
                             int packedLight, float alpha) {
        var builder = buffer.getBuffer(ModRenderType.plaqueIcon(ICONS));
        icon(builder, poseStack.last().pose(), x, y, 34, 9); // 满护甲图标,原色
    }

    @Override
    public int getValueColor(EntityStatusSnapshot snap) { return 0xFFE2E8F0; } // 钢白

    private void drawRect(Matrix4f m, MultiBufferSource b, int x, int y, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        VertexConsumer v = b.getBuffer(ModRenderType.barRect());
        v.vertex(m, x, y, 0).color(c).endVertex();
        v.vertex(m, x, y + h, 0).color(c).endVertex();
        v.vertex(m, x + w, y + h, 0).color(c).endVertex();
        v.vertex(m, x + w, y, 0).color(c).endVertex();
    }
}
