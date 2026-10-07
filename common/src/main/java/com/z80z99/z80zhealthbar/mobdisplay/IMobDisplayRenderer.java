package com.z80z99.z80zhealthbar.mobdisplay;

import com.z80z99.z80zhealthbar.status.EntityStatusSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

/**
 * 统一生物显示渲染器接口。
 * 渲染器只读 {@link EntityStatusSnapshot}，不直接访问实体（数据/渲染解耦，任务书 3.5）。
 */
public interface IMobDisplayRenderer {

    ResourceLocation getKey();

    /** 是否应该为该快照渲染（数值有效性判断，如护甲>0、水下才显示氧气） */
    boolean wantsToRender(EntityStatusSnapshot snap);

    /** 渲染条形样式（样式 C 主条 + 附加行）。
     *  worldScale = 当前 billboard 的世界缩放系数（0.025 × 各类缩放配置 × 距离因子），
     *  层间深度偏移需除以它换算回缩放空间，否则会被缩放吞掉导致共面 z-fighting。 */
    void renderBar(PoseStack poseStack, MultiBufferSource buffer, Font font,
                   EntityStatusSnapshot snap, int x, int y, int width, float alpha, float worldScale);

    /** 渲染牌匾图标行（样式 B 主行 + 样式 A/C 附加行）。worldScale 语义同上 */
    void renderPlaque(PoseStack poseStack, MultiBufferSource buffer,
                      EntityStatusSnapshot snap, int x, int y, Font font,
                      int packedLight, float alpha, float worldScale);

    /** 条形宽度（像素） */
    int getBarWidth(EntityStatusSnapshot snap);

    /** 牌匾图标行宽度（像素，不含数值文本） */
    int getPlaqueWidth(Font font, EntityStatusSnapshot snap);

    /** 牌匾右侧数值文本（如 "12"），null 表示无文本 */
    default String getValueText(EntityStatusSnapshot snap) { return null; }

    /** 数值文本颜色（可随数值渐变） */
    default int getValueColor(EntityStatusSnapshot snap) { return 0xFFFFFFFF; }

    /** 徽章式附加行（状态牌美术,2026-10 重设计）：在 (x,y) 绘制**单个** 9x9 状态图标。
     *  与 renderPlaque 的多图标行并存——牌匾样式 B 仍用图标行,样式 A/C 的附加行用徽章。 */
    default void renderBadge(PoseStack poseStack, MultiBufferSource buffer,
                             EntityStatusSnapshot snap, int x, int y,
                             int packedLight, float alpha) {
    }

    /** 徽章数值文本（状态牌用）。默认同 getValueText;如氧气显示气泡数而非 tick 数 */
    default String getBadgeValue(EntityStatusSnapshot snap) { return getValueText(snap); }
}
