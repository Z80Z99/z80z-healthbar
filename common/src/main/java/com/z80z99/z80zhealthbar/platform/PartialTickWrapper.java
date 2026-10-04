package com.z80z99.z80zhealthbar.platform;

/**
 * 跨版本 partialTick 隔离层。
 *
 * <p>V1 (1.20.1): 各 GUI 事件回调用 float 传入，本类作为 float 的简单包装。
 * <p>V1.5 (1.21.x): vanilla 改用 {@code DeltaTracker}，届时只需在本类新增
 * {@code from(DeltaTracker)} 静态工厂，common 层调用方无须改动。
 */
public final class PartialTickWrapper {
    private final float partialTick;

    public PartialTickWrapper(float partialTick) {
        this.partialTick = partialTick;
    }

    /** V1.20.1 路径：直接由 float 构造。 */
    public static PartialTickWrapper of(float partialTick) {
        return new PartialTickWrapper(partialTick);
    }

    /** 取出底层 float（V1 通用，V1.5 后仅在平台层调用）。 */
    public float asFloat() {
        return partialTick;
    }
}