package com.z80z99.z80zhealthbar.datasource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 单个类的成员扫描（只加载用户点开的那一个类，不批量加载）。
 *
 * <p>候选规则全部是确定性的结构判断（public/无参/返回数值类型），
 * 语义判断不在这里做——见 {@link CandidateRanker}。
 *
 * <p>安全：{@code Class.forName(name, false, loader)} 不触发静态初始化；
 * 逐成员捕获 {@link LinkageError}/{@link SecurityException}，失败只跳过该成员。
 */
public final class ModMemberScanner {

    /** 值方法候选（无参） */
    public record Candidate(String name, String returnType, Method method, boolean hasParams) {}

    /** 方法链候选：取得对象的无参方法 + 该对象类型上的无参值方法 */
    public record ChainCandidate(String objectMethod, String valueMethod, String valueReturnType,
                                 Class<?> objectType) {}

    /** Capability 静态字段候选 */
    public record FieldCandidate(String name, String fieldType) {}

    /** 扫描结果（各节独立；类无法加载时 {@code loadError} 非空） */
    public record Result(List<Candidate> valueMethods,
                         List<Candidate> paramMethods,
                         List<ChainCandidate> chains,
                         List<FieldCandidate> capabilityFields,
                         List<String> maxMethodNames,
                         String loadError) {
        static Result error(String message) {
            return new Result(List.of(), List.of(), List.of(), List.of(), List.of(), message);
        }
    }

    /** 副作用方法前缀：绝不进入值候选/采样循环（未知模组的 getter 也可能写状态） */
    private static final String[] SIDE_EFFECT_PREFIXES = {
            "set", "add", "remove", "put", "apply", "tick", "update", "save", "load",
            "kill", "hurt", "damage", "give", "drop", "consume", "reset", "clear", "write"
    };

    private ModMemberScanner() {}

    /** 判断方法名是否命中副作用黑名单（大小写不敏感前缀匹配） */
    public static boolean isSideEffect(String methodName) {
        if (methodName == null) return false;
        String n = methodName.toLowerCase(Locale.ROOT);
        for (String p : SIDE_EFFECT_PREFIXES) {
            if (n.startsWith(p)) return true;
        }
        return false;
    }

    /** 返回类型是否为数值（Number 子类或数值基本类型） */
    static boolean isNumeric(Class<?> type) {
        if (type == null) return false;
        return Number.class.isAssignableFrom(type)
                || type == int.class || type == long.class || type == float.class
                || type == double.class || type == short.class || type == byte.class;
    }

    /** 是否是值得当作“数据对象”继续浅扫描的类型（排除 JDK/集合/字符串等）。
     *  注意:用户自定义的嵌套数据类（如 Fixture.Data）不是 JDK 类,必须放行。 */
    static boolean isDataObject(Class<?> type) {
        if (type == null || type.isPrimitive() || type.isEnum() || type.isArray()) return false;
        if (isNumeric(type) || type == String.class || type == CharSequence.class) return false;
        String n = type.getName();
        if (n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("com.google.")
                || n.startsWith("net.minecraft.")) return false;
        // 排除 Collection/Map/Optional 等容器（它们的方法无业务语义）
        return !java.util.Collection.class.isAssignableFrom(type)
                && !java.util.Map.class.isAssignableFrom(type)
                && !java.util.Optional.class.isAssignableFrom(type);
    }

    public static Result scan(String className, ClassLoader loader) {
        if (className == null || className.isBlank()) return Result.error("empty");
        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, loader == null
                    ? Thread.currentThread().getContextClassLoader() : loader);
        } catch (Throwable t) {
            return Result.error(t.getClass().getSimpleName());
        }

        List<Candidate> values = new ArrayList<>();
        List<Candidate> paramMethods = new ArrayList<>();
        List<ChainCandidate> chains = new ArrayList<>();
        List<String> maxNames = new ArrayList<>();

        Method[] methods;
        try {
            methods = clazz.getMethods();
        } catch (Throwable t) {
            methods = new Method[0];
        }
        for (Method m : methods) {
            try {
                if (!Modifier.isPublic(m.getModifiers())) continue;
                if (m.getDeclaringClass() == Object.class) continue;
                String name = m.getName();
                if (isSideEffect(name)) continue;
                Class<?> ret = m.getReturnType();

                if (m.getParameterCount() > 0) {
                    // 带参数方法单独分节：只列参数类型可解析的（数值/布尔/字符串/枚举）
                    if (allParamsSupported(m.getParameterTypes())) {
                        paramMethods.add(new Candidate(name, ret.getSimpleName(), m, true));
                    }
                    continue;
                }
                if (!isNumeric(ret)) {
                    // 非数值返回：可能是数据对象,浅扫描其实例上的无参值方法（方法链候选）
                    if (isDataObject(ret)) {
                        for (Method vm : safeMethods(ret)) {
                            try {
                                if (!Modifier.isPublic(vm.getModifiers())) continue;
                                if (vm.getParameterCount() != 0) continue;
                                if (vm.getDeclaringClass() == Object.class) continue;
                                if (isSideEffect(vm.getName())) continue;
                                if (!isNumeric(vm.getReturnType())) continue;
                                chains.add(new ChainCandidate(name, vm.getName(),
                                        vm.getReturnType().getSimpleName(), ret));
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    continue;
                }
                if (name.toLowerCase(Locale.ROOT).contains("max")) {
                    maxNames.add(name);
                }
                values.add(new Candidate(name, ret.getSimpleName(), m, false));
            } catch (Throwable ignored) {
                // 单个成员解析失败只跳过
            }
        }

        List<FieldCandidate> fields = new ArrayList<>();
        try {
            for (Field f : clazz.getFields()) {
                try {
                    if (!Modifier.isStatic(f.getModifiers())) continue;
                    String type = f.getType().getName();
                    if (type.contains("Capability") || type.contains("CapabilityToken")) {
                        fields.add(new FieldCandidate(f.getName(), f.getType().getSimpleName()));
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        values.sort(Comparator.comparing(Candidate::name, String.CASE_INSENSITIVE_ORDER));
        paramMethods.sort(Comparator.comparing(Candidate::name, String.CASE_INSENSITIVE_ORDER));
        maxNames.sort(String.CASE_INSENSITIVE_ORDER);
        return new Result(values, paramMethods, chains, fields, maxNames, null);
    }

    private static Method[] safeMethods(Class<?> type) {
        try {
            return type.getMethods();
        } catch (Throwable t) {
            return new Method[0];
        }
    }

    /** 参数类型是否可被 ArgLiteralParser 表达（基本类型/包装/String/枚举） */
    static boolean allParamsSupported(Class<?>[] params) {
        for (Class<?> p : params) {
            boolean ok = p.isPrimitive() || p.isEnum()
                    || p == String.class
                    || p == Integer.class || p == Long.class || p == Float.class
                    || p == Double.class || p == Short.class || p == Byte.class
                    || p == Boolean.class;
            if (!ok) return false;
        }
        return true;
    }

    /** 生成参数类型串（"/"分隔全限定名；基本类型用 int/long/...） */
    public static String paramTypesOf(Method m) {
        StringBuilder sb = new StringBuilder();
        Class<?>[] ps = m.getParameterTypes();
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(ps[i].getName());
        }
        return sb.toString();
    }

    /** 生成默认实参串（"/"分隔） */
    public static String defaultArgsOf(Method m) {
        StringBuilder sb = new StringBuilder();
        Class<?>[] ps = m.getParameterTypes();
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(ArgLiteralParser.defaultLiteral(ps[i]));
        }
        return sb.toString();
    }
}
