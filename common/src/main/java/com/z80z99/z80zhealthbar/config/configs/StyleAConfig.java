package com.z80z99.z80zhealthbar.config.configs;

/**
 * 样式 A（MobHealthBar）专属配置。字段与原 MOD 的 hpbar.* 配置一一对应（见 docs/source-mod-audit.md §1）。
 *
 * <p>外观拆为两轴：{@code barType} 形状（0-2：外框条/心形行/经典宽条）×
 * {@code colorVariant} 配色（0-3：钢/血/金/奥术）。旧 textureMode 双轨序列与合并期
 * barType 0-5 均由 ConfigValidator 一次性迁移（textureMode="V2" 哨兵保证幂等）。
 */
public class StyleAConfig {
    /** 已废弃：仅用于旧配置迁移判定（ORIGINAL=旧双轨序列；V2=已迁移到形状+配色），渲染不再读取 */
    public String textureMode = "V2";

    /** 血条形状：0 = 外框条（贴图外框+配色染色填充），1 = 心形行，2 = 经典宽条 */
    public int barType = 0;
    /** 配色方案：0 = 钢（红），1 = 血（橙），2 = 金（绿），3 = 奥术（蓝）。作用于外框条与经典宽条 */
    public int colorVariant = 0;
    public int offsetX = 0;
    public int offsetY = 0;
    /** 血条整体高度偏移（方块），原 MOD 固定 +1.2 */
    public double heightOffset = 1.2;
    public double scaleName = 1.0;
    public double scaleBar = 1.0;
    /** 独立宽度拉伸（仅横向；不影响高度与文字；≤0 视为 1.0 不拉伸） */
    public double scaleBarWidth = 1.0;
    /** 独立高度拉伸（仅纵向；不影响宽度与文字；≤0 视为 1.0 不拉伸） */
    public double scaleBarHeight = 1.0;
    public double scaleNums = 1.0;
    public boolean showName = true;
    public boolean showHp = true;
    /** 血条整体不透明度（0-255；名称与数值一同淡出） */
    public int opacity = 255;
    /** 数值文字水平/垂直偏移（像素；正值向右/向下）——与血条偏移独立 */
    public int numOffsetX = 0;
    public int numOffsetY = 0;

    /** 仅旧配置迁移逻辑使用 */
    public boolean isOriginalTexture() {
        return "ORIGINAL".equalsIgnoreCase(textureMode);
    }

    /** 形状数量（barType 取值范围） */
    public int shapeCount() {
        return 3;
    }
}

