package com.z80z99.z80zhealthbar.datasource;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 扫描/标签/推荐/探测的纯函数测试（不依赖 MC 运行时）。
 */
class DatasourceScanTest {

    // ================= ArgLiteralParser =================

    @Test
    void argLiteralsParseCommonForms() {
        var list = ArgLiteralParser.parseList("1/2.5/true/\"abc\"/SomeEnum.VALUE");
        assertNotNull(list);
        assertEquals(5, list.size());
        assertEquals(1L, list.get(0).value());
        assertEquals(2.5, list.get(1).value());
        assertEquals(Boolean.TRUE, list.get(2).value());
        assertEquals("abc", list.get(3).value());
        assertEquals("SomeEnum.VALUE", list.get(4).value());
        assertEquals("SomeEnum", list.get(4).typeHint());
    }

    @Test
    void argLiteralRejectsGarbage() {
        assertNull(ArgLiteralParser.parseList("1/@@@"));
        assertNull(ArgLiteralParser.parseOne(""));
        assertNull(ArgLiteralParser.parseOne("not a literal"));
    }

    @Test
    void coerceMatchesTargetTypes() {
        assertEquals(7, ArgLiteralParser.coerce(
                new ArgLiteralParser.Arg(7L, "long"), int.class));
        assertEquals(1.5f, ArgLiteralParser.coerce(
                new ArgLiteralParser.Arg(1.5, "double"), float.class));
        assertEquals("x", ArgLiteralParser.coerce(
                new ArgLiteralParser.Arg("x", "String"), String.class));
        assertEquals(Boolean.FALSE, ArgLiteralParser.coerce(
                new ArgLiteralParser.Arg(Boolean.FALSE, "boolean"), boolean.class));
    }

    // ================= SemanticDictionary =================

    @Test
    void semanticLabelsQualifyDerivativeNames() {
        var entry = SemanticDictionary.match("getThirst");
        assertNotNull(entry);
        assertEquals("口渴值", SemanticDictionary.qualifiedLabel(entry, "getThirst"));
        assertEquals("润泽度", SemanticDictionary.qualifiedLabel(entry, "getQuenched"));
        assertEquals("口渴饱和度", SemanticDictionary.qualifiedLabel(entry, "getThirstSaturation"));
        assertTrue(SemanticDictionary.isNegative(entry, "getQuenched"));
        assertFalse(SemanticDictionary.isNegative(entry, "getThirst"));
    }

    @Test
    void humanizeStripsPrefixAndSplitsCamel() {
        assertEquals("Thirst Saturation", SemanticDictionary.humanize("getThirstSaturation"));
        assertEquals("Thirsty", SemanticDictionary.humanize("isThirsty"));
        assertEquals("Stamina", SemanticDictionary.humanize("get_stamina"));
    }

    // ================= CandidateRanker =================

    @Test
    void thirstCandidatesRankMainValueFirst() {
        List<CandidateRanker.Input> inputs = List.of(
                new CandidateRanker.Input("getThirst", "int", 17f, 20f),
                new CandidateRanker.Input("getQuenched", "int", 5f, 20f),
                new CandidateRanker.Input("getThirstSaturation", "float", 5f, 20f),
                new CandidateRanker.Input("getThirstRate", "float", 0.2f, null));
        var ranked = CandidateRanker.rank(inputs);
        assertEquals("getThirst", ranked.get(0).input().methodName());
        assertTrue(ranked.get(0).recommended(), "clear winner gets the star");
        assertEquals("口渴值", ranked.get(0).label());
        // 润泽度/饱和度被识别为从属值，排在主值后面
        assertTrue(ranked.stream().anyMatch(r -> r.label().equals("润泽度")));
        assertTrue(ranked.stream().anyMatch(r -> r.label().equals("口渴饱和度")));
    }

    @Test
    void closeScoresDoNotGetRecommendation() {
        List<CandidateRanker.Input> inputs = List.of(
                new CandidateRanker.Input("getA", "int", 10f, 20f),
                new CandidateRanker.Input("getB", "int", 10f, 20f));
        var ranked = CandidateRanker.rank(inputs);
        assertFalse(ranked.get(0).recommended(), "tie must not claim a confident recommendation");
    }

    @Test
    void readFailureRanksLast() {
        List<CandidateRanker.Input> inputs = List.of(
                new CandidateRanker.Input("getThirst", "int", null, null),
                new CandidateRanker.Input("getWater", "int", 5f, 10f));
        var ranked = CandidateRanker.rank(inputs);
        assertEquals("getWater", ranked.get(0).input().methodName());
    }

    // ================= ModClassScanner =================

    @Test
    void classNameFilteringSkipsMetadataAndInnerClasses() {
        assertNull(ModClassScanner.toClassName("META-INF/versions/9/Foo.class"));
        assertNull(ModClassScanner.toClassName("module-info.class"));
        assertNull(ModClassScanner.toClassName("a/b/package-info.class"));
        assertNull(ModClassScanner.toClassName("a/b/Outer$Inner.class"));
        assertNull(ModClassScanner.toClassName("a/b/mixin/FooMixin.class"));
        assertNull(ModClassScanner.toClassName("Root.class"), "default package classes are skipped");
        assertEquals("dev.ghen.thirst.api.IThirst",
                ModClassScanner.toClassName("dev/ghen/thirst/api/IThirst.class"));
    }

    @Test
    void classFilterAndGrouping() {
        List<String> classes = List.of(
                "dev.ghen.thirst.api.IThirst",
                "dev.ghen.thirst.api.ModCapabilities",
                "dev.ghen.thirst.util.Helper");
        var filtered = ModClassScanner.filter(classes, "api");
        assertEquals(2, filtered.size());
        var grouped = ModClassScanner.groupByPackage(classes);
        assertEquals(2, grouped.size());
        assertEquals(2, grouped.get("dev.ghen.thirst.api").size());
        assertEquals("dev.ghen.thirst.api", ModClassScanner.packageOf(classes.get(0)));
        assertEquals("IThirst", ModClassScanner.simpleName(classes.get(0)));
    }

    @Test
    void sortForDisplayPrefersShorterSimpleNames() {
        List<String> classes = List.of(
                "x.y.AVeryLongClassNameForTesting",
                "x.y.Short");
        var sorted = ModClassScanner.sortForDisplay(classes, "");
        assertEquals("x.y.Short", sorted.get(0));
    }

    @Test
    void trimToPackageRootStripsBuildOutputPrefix() {
        var markers = java.util.Set.of("classes", "java", "main");
        assertEquals("dev/ghen/thirst/api/IThirst.class",
                ModClassScanner.trimToPackageRoot(
                        "build/classes/java/main/dev/ghen/thirst/api/IThirst.class", markers));
        // 无标记时原样返回
        assertEquals("dev/ghen/thirst/A.class",
                ModClassScanner.trimToPackageRoot("dev/ghen/thirst/A.class", markers));
    }

    @Test
    void scanDirectoryFindsClassesInDevLayout(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws java.io.IOException {
        // 模拟开发环境:build/classes/java/main/<包>/X.class
        java.nio.file.Path pkg = dir.resolve("build/classes/java/main/dev/example/mod");
        java.nio.file.Files.createDirectories(pkg);
        java.nio.file.Files.write(pkg.resolve("Api.class"), new byte[]{1});
        java.nio.file.Files.write(pkg.resolve("Api$Inner.class"), new byte[]{1});
        java.nio.file.Path mixinPkg = dir.resolve("build/classes/java/main/dev/example/mod/mixin");
        java.nio.file.Files.createDirectories(mixinPkg);
        java.nio.file.Files.write(mixinPkg.resolve("ApiMixin.class"), new byte[]{1});

        var classes = ModClassScanner.scanClasses(dir);
        assertTrue(classes.contains("dev.example.mod.Api"), "top-level class is discovered: " + classes);
        assertFalse(classes.stream().anyMatch(c -> c.contains("$")), "inner classes filtered");
        assertFalse(classes.stream().anyMatch(c -> c.toLowerCase().contains("mixin")), "mixin filtered");
        ModClassScanner.clearCache();
    }

    // ================= ModLangIndex =================

    @Test
    void langIndexReadsJarEntriesAndMatchesMethods(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws java.io.IOException {
        java.nio.file.Path jar = dir.resolve("fake-mod.jar");
        try (var zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("assets/thirst/lang/zh_cn.json"));
            zip.write("{\"item.thirst.thirst\":\"口渴值\",\"item.thirst.quenched\":\"润泽度\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("assets/thirst/lang/en_us.json"));
            zip.write("{\"item.thirst.thirst\":\"Thirst\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var idx = ModLangIndex.load(jar, "zh_cn");
        assertFalse(idx.isEmpty());
        assertEquals("口渴值", idx.lookup("getThirst"), "snake-case tail matches the lang key");
        assertEquals("润泽度", idx.lookup("getQuenched"));
        ModLangIndex.clearCache();
    }

    @Test
    void langIndexCleansFormattingCodes() {
        assertEquals("Thirst", ModLangIndex.clean("§aThirst"));
        assertEquals("Value", ModLangIndex.clean("Value %s"));
        assertEquals("Thirst", ModLangIndex.clean("{0} Thirst"));
    }

    // ================= ModMemberScanner =================

    /** 成员扫描测试夹具：值方法、上限、副作用方法、数据对象链、Capability 字段 */
    public static class Fixture {
        /** 模拟 Capability 令牌（真实环境为 Forge Capability<T>,这里用同名类型验证筛选规则） */
        public static class Capability<T> {
        }

        public static final Capability<Fixture> PLAYER_THIRST = new Capability<>();

        public int getThirst() { return 17; }
        public int getMaxThirst() { return 20; }
        public float getThirstSaturation() { return 5f; }
        public boolean isThirsty() { return true; }
        public void setThirst(int v) { }
        public int addThirst(int v) { return v; }
        public Data getData() { return new Data(); }
        public int getStat(String key) { return 0; }

        public static class Data {
            public int getValue() { return 3; }
            public void reset() { }
        }
    }

    @Test
    void memberScannerClassifiesCandidates() {
        var r = ModMemberScanner.scan(Fixture.class.getName(), Fixture.class.getClassLoader());
        assertNull(r.loadError());
        assertTrue(r.valueMethods().stream().anyMatch(c -> c.name().equals("getThirst")));
        assertTrue(r.valueMethods().stream().anyMatch(c -> c.name().equals("getMaxThirst")));
        assertTrue(r.maxMethodNames().contains("getMaxThirst"));
        assertTrue(r.valueMethods().stream().noneMatch(c -> c.name().equals("isThirsty")),
                "boolean is not a value candidate");
        assertTrue(r.valueMethods().stream().noneMatch(c -> c.name().startsWith("set")),
                "side-effect setters are excluded");
        assertTrue(r.valueMethods().stream().noneMatch(c -> c.name().startsWith("add")),
                "add* is in the side-effect blacklist");
        assertTrue(r.paramMethods().stream().anyMatch(c -> c.name().equals("getStat")),
                "parameterized numeric method listed separately");
        assertTrue(r.chains().stream().anyMatch(c -> c.objectMethod().equals("getData")
                        && c.valueMethod().equals("getValue")),
                "one-level object chain discovered");
        assertTrue(r.capabilityFields().stream().anyMatch(f -> f.name().equals("PLAYER_THIRST")),
                "capability-like static field listed");
    }

    @Test
    void sideEffectBlacklistIsCaseInsensitive() {
        assertTrue(ModMemberScanner.isSideEffect("setThirst"));
        assertTrue(ModMemberScanner.isSideEffect("AddThirst"));
        assertTrue(ModMemberScanner.isSideEffect("updateValue"));
        assertFalse(ModMemberScanner.isSideEffect("getThirst"));
        assertFalse(ModMemberScanner.isSideEffect("isThirsty"));
    }

    // ================= LabelResolver / LabelExchange =================

    @Test
    void labelKeyNormalizationAndValidation() {
        assertEquals("a.B#getX", LabelResolver.key("a.B", "getX"));
        assertTrue(LabelResolver.isValidKey("a.B#getX"));
        assertFalse(LabelResolver.isValidKey("nohash"));
        assertFalse(LabelResolver.isValidKey("#getX"));
        assertFalse(LabelResolver.isValidKey("a.B#"));
        assertFalse(LabelResolver.isValidKey("a#b#c"));
        assertEquals("a.B#getX", LabelResolver.normalizeKey("a.B#getX(int)"));
    }

    @Test
    void labelImportRejectsUnknownKeysAndBadJson() {
        Set<String> allowed = Set.of("a.B#getX");
        String good = "{\"labels\":{\"a.B#getX\":\"口渴值\",\"other.C#getY\":\"无关\"}}";
        var res = LabelExchange.importJson(good, allowed);
        assertNotNull(res);
        assertEquals(1, res.labels().size());
        assertEquals(1, res.ignored(), "key outside the candidate set is ignored");
        assertEquals("口渴值", res.labels().get("a.B#getX"));

        assertNull(LabelExchange.importJson("not json", allowed));
        var bare = LabelExchange.importJson("{\"a.B#getX\":\"血条\"}", allowed);
        assertNotNull(bare);
        assertEquals("血条", bare.labels().get("a.B#getX"));
    }

    @Test
    void labelImportDropsInvalidValues() {
        Set<String> allowed = Set.of("a.B#getX");
        var res = LabelExchange.importJson("{\"labels\":{\"a.B#getX\":\"  \"}}", allowed);
        assertNotNull(res);
        assertTrue(res.labels().isEmpty());
        assertEquals(1, res.ignored());
    }

    // ================= ProbeSession =================

    @Test
    void probeDetectsChangesAndCountsThem() {
        ProbeSession.clear();
        float[] value = {10f};
        ProbeSession.start("cls", "Thirst", List.of("口渴值"),
                List.of(ignored -> value[0]), 1_000L);
        assertEquals(1, ProbeSession.candidates().size());
        assertFalse(ProbeSession.candidates().get(0).changed());

        value[0] = 8f;
        ProbeSession.sample(2_000L);
        assertTrue(ProbeSession.candidates().get(0).changed());
        assertEquals(1, ProbeSession.candidates().get(0).changes);
        assertEquals(1, ProbeSession.changedCount());

        value[0] = 6f;
        ProbeSession.sample(3_000L);
        assertEquals(2, ProbeSession.candidates().get(0).changes);

        // 同值不重复计数
        ProbeSession.sample(4_000L);
        assertEquals(2, ProbeSession.candidates().get(0).changes);
        ProbeSession.clear();
    }

    @Test
    void probeAutoStopsAfterLimitAndRespectsCandidateCap() {
        ProbeSession.clear();
        var labels = new java.util.ArrayList<String>();
        var readers = new java.util.ArrayList<java.util.function.Function<Void, Float>>();
        for (int i = 0; i < 40; i++) {
            labels.add("c" + i);
            readers.add(ignored -> 1f);
        }
        ProbeSession.start("cls", "T", labels, readers, 0L);
        assertEquals(ProbeSession.MAX_CANDIDATES, ProbeSession.candidates().size());

        assertTrue(ProbeSession.isActive());
        ProbeSession.sample(ProbeSession.AUTO_STOP_MS + 1);
        assertTrue(ProbeSession.isStopped(), "session stops itself at the time limit");
        ProbeSession.clear();
    }

    @Test
    void probeResetsBaselineAndClearsOnAbsence() {
        ProbeSession.clear();
        float[] value = {5f};
        ProbeSession.start("cls", "T", List.of("v"), List.of(ignored -> value[0]), 0L);
        value[0] = 4f;
        ProbeSession.sample(1_000L);
        assertEquals(1, ProbeSession.changedCount());

        ProbeSession.resetBaseline(2_000L);
        assertEquals(0, ProbeSession.changedCount(), "reset restarts the baseline");

        ProbeSession.tickPresence(false, 3_000L);
        ProbeSession.tickPresence(false, 6_000L);
        assertFalse(ProbeSession.hasSession(), "session clears after the player is gone");
    }

    @Test
    void probeAbortsSlowCandidate() {
        ProbeSession.clear();
        ProbeSession.start("cls", "T", List.of("slow"), List.of(ignored -> {
            try {
                Thread.sleep(15);
            } catch (InterruptedException ignoredEx) {
            }
            return 1f;
        }), 0L);
        ProbeSession.sample(1_000L);
        assertTrue(ProbeSession.candidates().get(0).aborted, "slow reader gets fused");
        assertEquals("slow", ProbeSession.candidates().get(0).abortReason);
        ProbeSession.clear();
    }
}
