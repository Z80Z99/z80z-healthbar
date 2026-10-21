package com.z80z99.z80zhealthbar.datasource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 目标模组 jar 的类名扫描（只读条目名，不解析字节码、不加载类）。
 *
 * <p>性能约束：一次扫描只打开 jar 读中央目录，结果按「路径 + 修改时间」缓存；
 * 类名条数上限 {@link #MAX_CLASSES}，超出由调用方提示用搜索收窄。
 */
public final class ModClassScanner {

    /** 单次扫描返回的类上限（面板行数与内存的双重保护） */
    public static final int MAX_CLASSES = 300;

    private ModClassScanner() {}

    /** 缓存：jar 路径 + 修改时间 → 全部类名（未排序、未过滤） */
    private static final Map<String, CachedScan> CACHE = new LinkedHashMap<>();

    private record CachedScan(long modified, List<String> classes) {}

    /**
     * 扫描模组类路径（jar 或 classes 目录，开发环境常以目录形式加载）。
     *
     * @return 类全名列表（可能为空）；路径不存在/不可读返回空列表，绝不抛异常
     */
    public static List<String> scanClasses(Path jar) {
        if (jar == null) return List.of();
        try {
            long modified = java.nio.file.Files.getLastModifiedTime(jar).toMillis();
            String key = jar.toAbsolutePath().toString();
            CachedScan cached = CACHE.get(key);
            if (cached != null && cached.modified() == modified) return cached.classes();

            List<String> classes = java.nio.file.Files.isDirectory(jar)
                    ? scanDirectory(jar) : scanJar(jar);
            classes.sort(Comparator.naturalOrder());
            CACHE.put(key, new CachedScan(modified, classes));
            if (CACHE.size() > 64) {
                var it = CACHE.keySet().iterator();
                if (it.hasNext()) { it.next(); it.remove(); }
            }
            return classes;
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private static List<String> scanJar(Path jar) throws IOException {
        List<String> classes = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String className = toClassName(e.getName());
                if (className != null) classes.add(className);
            }
        }
        return classes;
    }

    /** 开发环境：classes 目录（可能含 build 中间产物，按输出目录相对路径还原类全名） */
    private static List<String> scanDirectory(Path root) {
        List<String> classes = new ArrayList<>();
        // 常见输出目录标记:遇到这些段就从其后开始算包路径
        java.util.Set<String> markers = java.util.Set.of(
                "classes", "java", "main", "output", "production");
        try (var stream = java.nio.file.Files.walk(root, 12)) {
            stream.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                try {
                    String rel = root.relativize(p).toString().replace('\\', '/');
                    String className = toClassName(trimToPackageRoot(rel, markers));
                    if (className != null) classes.add(className);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
        return classes;
    }

    /** 去掉输出目录前缀（build/classes/java/main/ → 空）;找不到标记时原样返回 */
    static String trimToPackageRoot(String relPath, java.util.Set<String> markers) {
        String[] parts = relPath.split("/");
        for (int i = parts.length - 2; i >= 0; i--) {
            if (markers.contains(parts[i])) {
                StringBuilder sb = new StringBuilder();
                for (int j = i + 1; j < parts.length; j++) {
                    if (sb.length() > 0) sb.append('/');
                    sb.append(parts[j]);
                }
                return sb.toString();
            }
        }
        return relPath;
    }

    /** class 条目名 → 类全名；被排除的条目返回 null */
    static String toClassName(String entryName) {
        if (entryName == null || !entryName.endsWith(".class")) return null;
        String path = entryName.substring(0, entryName.length() - ".class".length());
        if (path.isEmpty()) return null;
        String lower = path.toLowerCase(Locale.ROOT);
        // 目录/元数据/内部类/混淆产物
        if (lower.startsWith("meta-inf/") || lower.contains("/meta-inf/")) return null;
        if (lower.endsWith("module-info") || lower.endsWith("package-info")) return null;
        if (path.indexOf('$') >= 0) return null;
        if (lower.contains("mixin")) return null;
        // 只有包名+类名（至少一个 '.'）才是值得列出的类；默认包类跳过
        if (path.indexOf('/') < 0) return null;
        return path.replace('/', '.');
    }

    /** 搜索过滤（id/简单名/包名 包含匹配，大小写不敏感）；query 为空返回原列表 */
    public static List<String> filter(List<String> classes, String query) {
        if (query == null || query.isBlank()) return classes;
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String c : classes) {
            if (c.toLowerCase(Locale.ROOT).contains(q)) out.add(c);
        }
        return out;
    }

    /**
     * 排序：搜索命中优先（query 非空时越短越相关）→ 简单名短优先 → 字母序。
     * 只影响展示顺序，不改变候选集合。
     */
    public static List<String> sortForDisplay(List<String> classes, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<String> copy = new ArrayList<>(classes);
        copy.sort(Comparator
                .comparingInt((String c) -> q.isEmpty() ? 0
                        : (simpleName(c).toLowerCase(Locale.ROOT).contains(q) ? 0 : 1))
                .thenComparingInt(c -> simpleName(c).length())
                .thenComparing(c -> c, String.CASE_INSENSITIVE_ORDER));
        return copy;
    }

    /** 按包分组（保持传入顺序）；用于类列表页的节标题 */
    public static Map<String, List<String>> groupByPackage(List<String> classes) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String c : classes) {
            String pkg = packageOf(c);
            out.computeIfAbsent(pkg, k -> new ArrayList<>()).add(c);
        }
        return out;
    }

    /** 类全名的简单名 */
    public static String simpleName(String className) {
        if (className == null) return "";
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    /** 类全名的包名（无包 = 空串） */
    public static String packageOf(String className) {
        if (className == null) return "";
        int dot = className.lastIndexOf('.');
        return dot < 0 ? "" : className.substring(0, dot);
    }

    /** 清空缓存（配置重载/模组列表变化时可选调用） */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * 类加载器兜底定位（开发环境 Forge 常以 classpath 目录加载,ModList 不提供可读 jar 路径）：
     * 从 classloader 里找一个已知类的根目录,再向上找包含 assets/ 的目录。
     */
    public static Path locateFromClassLoader(ClassLoader loader, String probeClass, String modId) {
        if (loader == null) return null;
        try {
            Class<?> probe = Class.forName(probeClass, false, loader);
            var url = probe.getProtectionDomain().getCodeSource().getLocation();
            if (url == null) return null;
            Path root = Path.of(url.toURI());
            // 从该路径向上最多 6 层,找含 assets/<modId>/ 或 assets/ 的目录（资源与类通常同根）
            Path cur = root;
            for (int i = 0; i < 6 && cur != null; i++) {
                if (java.nio.file.Files.isDirectory(cur.resolve("assets"))) return cur;
                cur = cur.getParent();
            }
            // 类目录（build/classes/java/main）→ 回溯到工程内包含 run/ 或 resources 的根
            return root;
        } catch (Throwable t) {
            return null;
        }
    }

}
