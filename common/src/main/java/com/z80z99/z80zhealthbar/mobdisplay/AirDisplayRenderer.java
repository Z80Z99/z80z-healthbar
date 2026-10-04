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

/** 氧气渲染器：条形（附加行）+ 原版气泡牌匾行；仅水下（air < maxAir）显示 */
public class AirDisplayRenderer implements IMobDisplayRenderer {
    private static final ResourceLocation KEY = new ResourceLocation(Z80ZHealthBar.MOD_ID, "air");
    private static final ResourceLocation ICONS =
            new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final int MAX_BUBBLES = 10;

    @Override public ResourceLocation getKey() { return KEY; }

    @Override
    public boolean wantsToRender(EntityStatusSnapshot snap) {
        return snap.isUnderwater();
    }

    @Override
    public void renderBar(PoseStack poseStack, MultiBufferSource buffer, Font font,
                          EntityStatusSnapshot snap, int x, int y, int barWidth, float alpha, float worldScale) {
        var colors = ConfigManager.getConfig().colors;
        int barH = ConfigManager.getConfig().barStyle.barHalfHeight * 2;
        int fillColor = ColorHelper.modifyAlpha(ColorHelper.parseColor(colors.air), (int) (alpha * 255));
        int boundColor = ColorHelper.modifyAlpha(ColorHelper.parseColor(colors.airBound), (int) (alpha * 255));
        Matrix4f matrix = poseStack.last().pose();

        drawRect(matrix, buffer, x, y, barWidth, barH,
                ColorHelper.modifyAlpha(0x40000000, (int) (alpha * 255)));

        int fillW = (int) (snap.airSupply / (float) snap.maxAirSupply * barWidth);
        fillW = Math.max(0, Math.min(barWidth, fillW));
        if (fillW > 0) drawRect(matrix, buffer, x, y, fillW, barH, fillColor);

        drawRect(matrix, buffer, x, y, barWidth, 1, boundColor);
        drawRect(matrix, buffer, x, y + barH - 1, barWidth, 1, boundColor);
        drawRect(matrix, buffer, x, y, 1, barH, boundColor);
        drawRect(matrix, buffer, x + barWidth - 1, y, 1, barH, boundColor);
    }

    @Override
    public void renderPlaque(PoseStack poseStack, MultiBufferSource buffer,
                              EntityStatusSnapshot snap, int x, int y,
                              Font font, int packedLight, float alpha, float worldScale) {
        int curBubbles = (int) Math.ceil(snap.airSupply / (float) snap.maxAirSupply * MAX_BUBBLES);
        curBubbles = Math.min(curBubbles, MAX_BUBBLES);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer builder = buffer.getBuffer(ModRenderType.plaqueIcon(ICONS));

        // 原版 icons.png 仅两种气泡：满 (16,18)、将破 (25,18)；无空槽，用将破气泡做槽底
        for (int i = 0; i < MAX_BUBBLES; i++) {
            ArmorDisplayRenderer.icon(builder, matrix, x + i * 9, y, 25, 18);
        }
        if (curBubbles > 0) {
            poseStack.pushPose();
            poseStack.translate(0, 0, -0.02f / Math.max(1e-5f, worldScale));
            Matrix4f mFill = poseStack.last().pose();
            for (int i = 0; i < curBubbles; i++) {
                ArmorDisplayRenderer.icon(builder, mFill, x + i * 9, y, 16, 18);
            }
            poseStack.popPose();
        }
    }

    @Override public int getBarWidth(EntityStatusSnapshot snap) { return 60; }
    @Override public int getPlaqueWidth(Font font, EntityStatusSnapshot snap) { return MAX_BUBBLES * 9; }

    @Override
    public String getValueText(EntityStatusSnapshot snap) { return String.valueOf(snap.airSupply); }

    private void drawRect(Matrix4f m, MultiBufferSource b, int x, int y, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        VertexConsumer v = b.getBuffer(ModRenderType.barRect());
        v.vertex(m, x, y, 0).color(c).endVertex();
        v.vertex(m, x, y + h, 0).color(c).endVertex();
        v.vertex(m, x + w, y + h, 0).color(c).endVertex();
        v.vertex(m, x + w, y, 0).color(c).endVertex();
    }
}
