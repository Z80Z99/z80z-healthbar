package com.z80z99.z80zhealthbar.platform;

/**
 * 平台服务注入点。平台启动期一次性 set，common 调用方 get 取实例。
 * Fabric 专用服务器无客户端入口时 helper 恒为 null —— 网络层等调用方须先经 has() 防护。
 */
public final class PlatformService {
    private static IPlatformHelper helper;

    private PlatformService() {}

    public static IPlatformHelper get() {
        if (helper == null) {
            throw new IllegalStateException("PlatformHelper has not been set! Call setHelper() in mod initializer.");
        }
        return helper;
    }

    /** 平台实现是否已注入（专用服务器/早期类加载阶段为 false） */
    public static boolean has() {
        return helper != null;
    }

    public static void setHelper(IPlatformHelper platformHelper) {
        if (helper != null) {
            throw new IllegalStateException("PlatformHelper has already been set!");
        }
        helper = platformHelper;
    }
}
