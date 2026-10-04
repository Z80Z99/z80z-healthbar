package com.z80z99.z80zhealthbar.config.configs;

public class BarStyleConfig {
    public boolean enableHealthBar = true;
    /** 长条填充外观：0 = 平面，1 = 渐变，2 = 分段，3 = 高光（原版复刻模式不适用） */
    public int barVariant = 0;
    /** 分段（变体 2）固定格数：1-64，默认 10；每格血量 = 最大血量 ÷ 格数（segmentHp > 0 时以它为准） */
    public int segmentCount = 10;
    /** 分段（变体 2）每格血量：0 = 关闭（按格数均分）；正数 = 固定每格血量，格数随最大血量伸缩（超 64 格合并） */
    public int segmentHp = 0;
    /** 分段（变体 2）只显示完整格：不足一格的剩余血量不显示 */
    public boolean segmentWholeOnly = false;
    public double barScale = 1.0;
    public double barOffsetY = 0.5;
    /** 血条像素偏移（条像素；与文字偏移同空间：正值向右/向下；文字随血条移动） */
    public int barPixelOffsetX = 0;
    public int barPixelOffsetY = 0;
    /**
     * 血条半宽（0 = 自动）。自动：宽度跟随实体最大血量（每点 2px，钳制 20-80px）；
     * 非零：固定全宽 = 半宽 × 2（8-160px）。此前该字段从未被渲染器读取（死配置），
     * 由 configVersion 3 迁移统一置 0 保持既有观感。
     */
    public int barHalfWidth = 0;
    public int barHalfHeight = 2;
    public int barBoundWidth = 1;
    public double barTextScale = 0.5;
    public double barTextOffsetX = 0.0;
    public double barTextOffsetY = 0.0;
    public int barAlpha = 255;
    public boolean barBoundVertex = false;
    public boolean healthBarHealthColorDynamic = true;
    /** 调试：按反编译源逐行复刻 AsteorBar 实体血条渲染（lightmap 渐变管线 + 吸收环） */
    public boolean originalRender = false;
    /** 实体血条数值取整显示（默认开；适用于样式 1/3 的数值文本，样式 2 牌匾为整数设计；
     *  原版复刻调试模式忠实显示小数，不受此开关影响） */
    public boolean integerHealthText = true;
}
