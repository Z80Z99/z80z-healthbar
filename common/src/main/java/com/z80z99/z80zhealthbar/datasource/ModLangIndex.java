package com.z80z99.z80zhealthbar.datasource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 目标模组 jar 内的语言文件索引（把方法名匹配到可读词条）。
 *
 * <p>只读取 {@code assets/<ns>/lang/<lang>.json},不加载类。解析放在调用方线程（页面打开时一次）,
 * 结果按 jar 路径缓存;文件过大（&gt;2MB 未压缩）时跳过,避免卡 GUI。
 */
public final class ModLangIndex {

    /** 单个 lang 文件上限（未压缩字节）;超过则不解析 */
    private static final long MAX_LANG_BYTES = 2L * 1024 * 1024;

    /** key（小写,点/下划线归一） → 文本 */
    private final Map<String, String> entries = new LinkedHashMap<>();
    /** 原始文本（保序,用于“包含该词的最短键”匹配） */
    private final List<Map.Entry<String, String>> ordered = new ArrayList<>();

    private ModLangIndex() {}

    /** 缓存:jar 路径 + 修改时间 → 索引（未命中 = 空索引） */
    private static final Map<String, ModLangIndex> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> CACHE_TIME = new ConcurrentHashMap<>();

    /** 读取 jar 的 lang（当前语言优先,回退 en_us）;失败返回空索引,绝不抛异常 */
    public static ModLangIndex load(java.nio.file.Path jar, String preferredLang) {
        if (jar == null) return empty();
        try {
            long modified = java.nio.file.Files.getLastModifiedTime(jar).toMillis();
            String key = jar.toAbsolutePath().toString() + "#" + preferredLang;
            Long at = CACHE_TIME.get(key);
            if (at != null && at == modified) {
                return CACHE.getOrDefault(key, empty());
            }
            ModLangIndex idx = parse(jar, preferredLang);
            CACHE.put(key, idx);
            CACHE_TIME.put(key, modified);
            return idx;
        } catch (Throwable t) {
            return empty();
        }
    }

    private static ModLangIndex parse(java.nio.file.Path jar, String preferredLang) {
        ModLangIndex idx = new ModLangIndex();
        try {
            if (java.nio.file.Files.isDirectory(jar)) {
                parseDirectory(idx, jar, preferredLang);
                return idx;
            }
        } catch (Throwable ignored) {
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            String lang = preferredLang == null || preferredLang.isBlank() ? "en_us" : preferredLang;
            // 先收集中文/目标语言,再补 en_us（前者优先,不覆盖）
            List<ZipEntry> order = new ArrayList<>();
            for (String candidate : new String[]{lang, "zh_cn", "en_us"}) {
                ZipEntry e = findLangEntry(zip, candidate);
                if (e != null && order.stream().noneMatch(x -> x.getName().equals(e.getName()))) {
                    order.add(e);
                }
            }
            for (ZipEntry e : order) {
                if (e.getSize() > MAX_LANG_BYTES) continue;
                try (Reader r = new InputStreamReader(zip.getInputStream(e), StandardCharsets.UTF_8)) {
                    idx.readInto(r);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return idx;
    }

    /** 开发环境:从 classes 目录里找 assets/<ns>/lang/<lang>.json */
    private static void parseDirectory(ModLangIndex idx, java.nio.file.Path root, String preferredLang) {
        String lang = preferredLang == null || preferredLang.isBlank() ? "en_us" : preferredLang;
        for (String candidate : new String[]{lang, "zh_cn", "en_us"}) {
            try (var stream = java.nio.file.Files.walk(root, 12)) {
                var found = stream.filter(p -> p.toString().replace('\\', '/')
                                .matches(".*/assets/[^/]+/lang/" + java.util.regex.Pattern.quote(candidate) + "\\.json"))
                        .findFirst();
                if (found.isPresent()) {
                    try (Reader r = java.nio.file.Files.newBufferedReader(found.get(), StandardCharsets.UTF_8)) {
                        idx.readInto(r);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** 读取一个 lang JSON 到索引（已存在的键不覆盖,保证语言优先级） */
    private void readInto(Reader reader) {
        try {
            Map<?, ?> map = new com.google.gson.Gson().fromJson(reader, Map.class);
            if (map == null) return;
            for (var en : map.entrySet()) {
                if (en.getKey() == null || en.getValue() == null) continue;
                String k = normalizeKey(String.valueOf(en.getKey()));
                String v = clean(String.valueOf(en.getValue()));
                if (k.isEmpty() || v.isEmpty()) continue;
                putIfAbsent(k, v);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 在 jar 中找 assets/<任意 ns>/lang/<lang>.json（取第一个匹配） */
    private static ZipEntry findLangEntry(ZipFile zip, String lang) {
        String suffix = "/lang/" + lang + ".json";
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            String name = e.getName();
            if (name.startsWith("assets/") && name.endsWith(suffix)) return e;
        }
        return null;
    }

    private void putIfAbsent(String key, String value) {
        if (entries.containsKey(key)) return;
        entries.put(key, value);
        ordered.add(Map.entry(key, value));
    }

    /**
     * 方法名 → 语言文件标签。
     * <p>匹配顺序:方法名转 snake_case 精确命中词条键（键的末段相等）→ 文本包含该词的最短条目。
     *
     * @return 标签；未命中返回 null
     */
    public String lookup(String methodName) {
        if (methodName == null || methodName.isBlank() || entries.isEmpty()) return null;
        String snake = toSnake(methodName);
        // getter 前缀剥离后的核心词（get_thirst → thirst）:语言键末段通常不含 get_
        String core = snake;
        for (String p : new String[]{"get_", "is_", "has_", "can_", "should_"}) {
            if (core.startsWith(p) && core.length() > p.length()) {
                core = core.substring(p.length());
                break;
            }
        }
        // 1) 键末段精确匹配（get_thirst / thirst 都能命中 item.thirst.thirst）
        for (var e : ordered) {
            String key = e.getKey();
            String tail = key.substring(key.lastIndexOf('.') + 1);
            if (tail.equals(core) || tail.equals(snake)) return e.getValue();
        }
        // 2) 文本包含（大小写不敏感）→ 取文本最短的条目（通常是最直接的名称）
        String word = core.replace('_', ' ');
        String best = null;
        for (var e : ordered) {
            String text = e.getValue().toLowerCase(Locale.ROOT);
            if (text.contains(word) || text.contains(core) || text.contains(snake)) {
                if (best == null || e.getValue().length() < best.length()) best = e.getValue();
            }
        }
        return best;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 清空缓存（配置重载时可选） */
    public static void clearCache() {
        CACHE.clear();
        CACHE_TIME.clear();
    }

    private static ModLangIndex empty() {
        return new ModLangIndex();
    }

    /** 键归一:小写、下划线保留（末段匹配需要） */
    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).trim();
    }

    /** 去 § 格式码、printf/%s/{0} 占位符 */
    static String clean(String text) {
        if (text == null) return "";
        String s = text.replaceAll("§.", "");
        s = s.replaceAll("%\\d*\\$?s", "");
        s = s.replaceAll("\\{\\d+}", "");
        return s.trim();
    }

    /** 驼峰 → snake_case（getThirstSaturation → get_thirst_saturation） */
    static String toSnake(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (sb.length() > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else if (c == '(' || c == ')') {
                continue;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
