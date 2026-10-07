package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.platform.PlatformService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 外部图标贴图（图标来源可选"外部自定义"）：扫描 config/z80zhealthbar/icons/*.png，
 * 按文件名懒加载注册为动态贴图；渲染时整图缩放为 9×9。文件名不含扩展名即来源标识。
 */
public final class IconTextures {

    private static final Map<String, ResourceLocation> LOADED = new HashMap<>();
    /** 已知缺失文件名 → 到期重试时间（负结果缓存——此前失败路径每帧每图标一次文件系统探测） */
    private static final Map<String, Long> MISSING = new HashMap<>();
    private static final long MISSING_RETRY_MS = 10_000L;
    private static List<String> cachedNames;
    private static long cachedAt;

    /** 可用外部贴图文件名（不含扩展名；目录扫描结果 10 秒缓存） */
    public static List<String> available() {
        long now = System.currentTimeMillis();
        if (cachedNames != null && now - cachedAt < 10_000) return cachedNames;
        List<String> out = new ArrayList<>();
        try {
            File dir = iconDir().toFile();
            if (dir.isDirectory()) {
                File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".png"));
                if (files != null) {
                    for (File f : files) out.add(f.getName().replaceAll("(?i)\\.png$", ""));
                }
            }
        } catch (Exception ignored) {
            // 目录不可读时视为无外部图标
        }
        Collections.sort(out);
        cachedNames = out;
        cachedAt = now;
        return out;
    }

    /** 取外部贴图（懒加载;文件缺失/读取失败返回 null → 调用方回退原版图标。
     *  负结果缓存 10 秒（避免渲染线程每帧文件系统探测）,到期自动重试支持热替换） */
    public static ResourceLocation get(String name) {
        if (name == null || name.isBlank()) return null;
        ResourceLocation cached = LOADED.get(name);
        if (cached != null) return cached;
        Long retryAt = MISSING.get(name);
        if (retryAt != null && System.currentTimeMillis() < retryAt) return null;
        try {
            File f = iconDir().resolve(name + ".png").toFile();
            if (!f.isFile()) {
                MISSING.put(name, System.currentTimeMillis() + MISSING_RETRY_MS);
                return null;
            }
            var img = com.mojang.blaze3d.platform.NativeImage.read(Files.newInputStream(f.toPath()));
            ResourceLocation id = new ResourceLocation("z80zhealthbar",
                    "icons/" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_"));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
            LOADED.put(name, id);
            MISSING.remove(name);
            return id;
        } catch (Exception e) {
            MISSING.put(name, System.currentTimeMillis() + MISSING_RETRY_MS);
            return null;
        }
    }

    private static java.nio.file.Path iconDir() {
        return PlatformService.get().getConfigDir().resolve("z80zhealthbar").resolve("icons");
    }

    private IconTextures() {}
}
