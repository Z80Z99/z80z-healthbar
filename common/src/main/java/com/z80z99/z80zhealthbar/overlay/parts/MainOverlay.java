package com.z80z99.z80zhealthbar.overlay.parts;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.OverlayConfig;
import com.z80z99.z80zhealthbar.overlay.*;
import com.z80z99.z80zhealthbar.util.Pair;
import net.minecraft.client.gui.GuiGraphics;

public class MainOverlay extends BaseOverlay {

    @Override
    public void renderOverlay(RenderGui renderGui, GuiGraphics graphics, float pt, int screenW, int screenH) {
        var cfg = ConfigManager.getConfig().overlay;
        int style = cfg.overlayLayoutStyle;
        if (style == 0) return;

        if (style <= 2) {
            // Wave 12 修复：样式 1/2（物品栏上方）改为底部锚定固定槽位布局。
            // 人物血条底部贴快捷栏上方（原版心形底部 = screenH - 30），
            // 其余条依次向上排列，不再从屏幕顶部向下堆叠。
            renderAboveHotbar(renderGui, graphics, pt, screenW, screenH, cfg);
        } else {
            // 样式 3-8（两侧/角落）→ 配置的角部垂直边距，向下堆叠
            int startY = cfg.cornerVerticalPadding;
            renderGui.setLeftHeight(startY);
            renderGui.setRightHeight(startY);
            for (Pair<BaseOverlay, OverlayPosition> pair : OverlayManager.getCurrentOrder()) {
                BaseOverlay overlay = pair.getKey();
                if (overlay instanceof SimpleBarOverlay bar) {
                    bar.setDefinedPosition(pair.getValue());
                }
                overlay.render(renderGui, graphics, pt, screenW, screenH);
            }
        }

        // 最后渲染文本层
        OverlayManager.STRING.render(renderGui, graphics, pt, screenW, screenH);
    }

    /**
     * 样式 1/2：物品栏上方固定槽位布局（从下往上）：
     * 玩家血条 → 食物 → 空气 → 经验；坐骑/护甲与经验条同行（左右两侧）。
     * 固定槽位保证条件条（空气/坐骑）不渲染时布局不跳动。
     */
    private void renderAboveHotbar(RenderGui renderGui, GuiGraphics graphics, float pt, int screenW, int screenH, OverlayConfig cfg) {
        int barH = cfg.overlayBarInnerHeight;
        int margin = cfg.overlayBarVerticalMargin;
        int pitch = barH + margin;
        // 玩家血条底部贴快捷栏上方（原版心形底部 = screenH - 30）
        int playerTop = screenH - 30 - barH;

        for (Pair<BaseOverlay, OverlayPosition> pair : OverlayManager.getCurrentOrder()) {
            BaseOverlay overlay = pair.getKey();
            if (overlay instanceof SimpleBarOverlay bar) {
                bar.setDefinedPosition(pair.getValue());
            }

            int top;
            if (overlay == HudRenderer.PLAYER_HEALTH) top = playerTop;
            else if (overlay == HudRenderer.FOOD_LEVEL) top = playerTop - pitch;
            else if (overlay == HudRenderer.AIR_LEVEL) top = playerTop - 2 * pitch;
            else if (overlay == HudRenderer.EXPERIENCE_BAR
                    || overlay == HudRenderer.MOUNT_HEALTH
                    || overlay == HudRenderer.ARMOR_LEVEL) top = playerTop - 3 * pitch;
            else top = renderGui.getLeftHeight(margin); // 其他（compat）跟随堆叠

            // 预置位置计数器：条内 getLeftHeight(margin) 取 cornerLeftHeight + margin，故预置 top - margin
            boolean useRight = pair.getValue() == OverlayPosition.RIGHT;
            if (overlay == HudRenderer.MOUNT_HEALTH) useRight = !cfg.mountHealthOnLeftSide;
            if (useRight) renderGui.setRightHeight(top - margin);
            else renderGui.setLeftHeight(top - margin);

            overlay.render(renderGui, graphics, pt, screenW, screenH);
        }
    }
}
