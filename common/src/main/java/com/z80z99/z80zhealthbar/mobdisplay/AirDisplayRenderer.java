package com.z80z99.z80zhealthbar.mobdisplay;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.status.EntityStatusSnapshot;
import com.z80z99.z80zhealthbar.util.AirBubbleRow;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** 氧气渲染器：条形（附加行）+ 原版气泡牌匾行；空气未满时显示（耗尽后整行消失） */
public class AirDisplayRenderer implements IMobDisplayRenderer {
    private static final ResourceLocation KEY = new ResourceLocation(Z80ZHealthBar.MOD_ID, "air");
    private static final ResourceLocation ICONS =
            new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final int MAX_BUBBLES = 10;

    @Override public ResourceLocation getKey() { return KEY; }

    @Override
    public boolean wantsToRender(EntityStatusSnapshot snap) {
        // 空气未满（isUnderwater 即 air < max：水下掉氧或出水回氧）且还有残余氧气。
        // 氧气耗尽（0）整行消失——原版 0 氧气不画任何气泡；此前无此判据,耗尽后整行仍在
        return snap.isUnderwater() && snap.airSupply > 0;
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
        // 原版气泡语义（见 AirBubbleRow）：满泡 + 至多一个正在破裂的边界泡,其余槽位不绘制。
        // 此前把"将破"贴图铺满 10 格当槽底 → 破掉的气泡看起来永久残留（实测"泡沫爆裂后不消失"）
        int[] counts = AirBubbleRow.counts(snap.airSupply, snap.maxAirSupply, MAX_BUBBLES);
        int full = counts[0];
        int count = full + counts[1];
        if (count <= 0) return; // 空槽/零氧气：不画任何气泡

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer builder = buffer.getBuffer(ModRenderType.plaqueIcon(ICONS));
        for (int i = 0; i < count; i++) {
            // 原版 icons.png 仅两种气泡：满 (16,18)、将破 (25,18)
            ArmorDisplayRenderer.icon(builder, matrix, x + i * 9, y, i < full ? 16 : 25, 18);
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
