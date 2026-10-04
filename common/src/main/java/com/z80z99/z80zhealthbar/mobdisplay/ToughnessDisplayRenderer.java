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

/** 护甲韧性渲染器：读取 ARMOR_TOUGHNESS 属性（区别于护甲值） */
public class ToughnessDisplayRenderer implements IMobDisplayRenderer {
    private static final ResourceLocation KEY = new ResourceLocation(Z80ZHealthBar.MOD_ID, "toughness");
    private static final ResourceLocation ICONS =
            new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final int MAX_SLOTS = 4;

    @Override public ResourceLocation getKey() { return KEY; }

    @Override
    public boolean wantsToRender(EntityStatusSnapshot snap) {
        return snap.hasToughness();
    }

    @Override
    public void renderBar(PoseStack poseStack, MultiBufferSource buffer, Font font,
                          EntityStatusSnapshot snap, int x, int y, int barWidth, float alpha, float worldScale) {
        var colors = ConfigManager.getConfig().colors;
        int barH = ConfigManager.getConfig().barStyle.barHalfHeight * 2;
        int fillColor = ColorHelper.modifyAlpha(ColorHelper.parseColor(colors.armorToughness), (int) (alpha * 255));
        int boundColor = ColorHelper.modifyAlpha(ColorHelper.parseColor(colors.armorBound), (int) (alpha * 255));

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer v = buffer.getBuffer(ModRenderType.barRect());
        drawRect(v, matrix, x, y, barWidth, barH,
                ColorHelper.modifyAlpha(0x40000000, (int) (alpha * 255)));

        int fillW = (int) (snap.armorToughness / 12.0 * barWidth);
        fillW = Math.max(0, Math.min(barWidth, fillW));
        if (fillW > 0) drawRect(v, matrix, x, y, fillW, barH, fillColor);

        drawBoundRect(v, matrix, x, y, barWidth, barH, boundColor);
    }

    @Override
    public void renderPlaque(PoseStack poseStack, MultiBufferSource buffer,
                              EntityStatusSnapshot snap, int x, int y,
                              Font font, int packedLight, float alpha, float worldScale) {
        if (snap.armorToughness <= 0) return;
        int curSlots = (int) Math.min(MAX_SLOTS, Math.ceil(snap.armorToughness / 4.0));

        Matrix4f matrix = poseStack.last().pose();
        // 原版 icons.png 无韧性专属图标：沿用护甲图标但整体染青蓝色，与护甲行明确区分
        VertexConsumer builder = buffer.getBuffer(ModRenderType.tintedIcon(ICONS));

        for (int i = 0; i < MAX_SLOTS; i++) {
            ArmorDisplayRenderer.iconTinted(builder, matrix, x + i * 9, y, 16, 9, 0xFF3A5A78);
        }
        if (curSlots > 0) {
            poseStack.pushPose();
            poseStack.translate(0, 0, -0.02f / Math.max(1e-5f, worldScale));
            Matrix4f mFill = poseStack.last().pose();
            for (int i = 0; i < curSlots; i++) {
                ArmorDisplayRenderer.iconTinted(builder, mFill, x + i * 9, y, 34, 9, 0xFF7FD4F0);
            }
            poseStack.popPose();
        }
    }

    @Override public int getBarWidth(EntityStatusSnapshot snap) {
        return Math.min(80, Math.max(20, (int) (snap.armorToughness * 2.5)));
    }

    @Override public int getPlaqueWidth(Font f, EntityStatusSnapshot snap) { return MAX_SLOTS * 9; }

    @Override
    public String getValueText(EntityStatusSnapshot snap) { return String.valueOf(snap.armorToughness); }

    private static void drawRect(VertexConsumer v, Matrix4f m, int x, int y, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        v.vertex(m, x, y, 0).color(c).endVertex();
        v.vertex(m, x, y + h, 0).color(c).endVertex();
        v.vertex(m, x + w, y + h, 0).color(c).endVertex();
        v.vertex(m, x + w, y, 0).color(c).endVertex();
    }

    private static void drawBoundRect(VertexConsumer v, Matrix4f matrix, int x, int y, int w, int h, int c) {
        drawRect(v, matrix, x, y, w, 1, c);
        drawRect(v, matrix, x, y + h - 1, w, 1, c);
        drawRect(v, matrix, x, y, 1, h, c);
        drawRect(v, matrix, x + w - 1, y, 1, h, c);
    }
}
