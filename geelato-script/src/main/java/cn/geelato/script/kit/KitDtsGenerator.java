package cn.geelato.script.kit;

import cn.geelato.core.graal.GraalFunctionDescription;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * catalog → TypeScript 声明文件：规则作者把产物放进工程即可在 IDE 中获得 $gl/$kit 的 intellisense。
 */
public final class KitDtsGenerator {

    private KitDtsGenerator() {
    }

    public static String generate(KitRegistry registry) {
        return generate(registry.getDefinitions().values());
    }

    public static String generate(Collection<KitDefinition> definitions) {
        List<KitDefinition> built = new ArrayList<>();
        List<KitDefinition> topLevel = new ArrayList<>();
        for (KitDefinition def : definitions) {
            (def.isBuilt() ? built : topLevel).add(def);
        }
        StringBuilder out = new StringBuilder();
        out.append("/** Geelato script kit 声明（KitDtsGenerator 生成；运行时 $gl.help() 输出同一数据源）。 */\n");
        if (!built.isEmpty()) {
            out.append("declare const $gl: {\n");
            for (KitDefinition def : built) {
                appendNamespace(out, def, "  ");
            }
            out.append("};\n");
        }
        for (KitDefinition def : topLevel) {
            out.append("declare const ").append(def.exposeName()).append(": {\n");
            appendNamespace(out, def, "  ");
            out.append("};\n");
        }
        return out.toString();
    }

    private static void appendNamespace(StringBuilder out, KitDefinition def, String indent) {
        out.append(indent).append("/** ").append(comment(def.getDescription())).append(" */\n");
        out.append(indent).append(def.getName()).append(": {\n");
        for (Method m : def.getFunctions()) {
            appendMethod(out, def, m, indent + "  ");
        }
        out.append(indent).append("};\n");
    }

    private static void appendMethod(StringBuilder out, KitDefinition def, Method m, String indent) {
        GraalFunctionDescription fn = describe(def, m);
        out.append(indent).append("/**\n");
        if (fn.getDescription() != null && !fn.getDescription().isBlank()) {
            out.append(indent).append(" * ").append(comment(fn.getDescription())).append('\n');
        }
        if (fn.getExample() != null && !fn.getExample().isBlank()) {
            out.append(indent).append(" * @example ").append(comment(fn.getExample())).append('\n');
        }
        out.append(indent).append(" */\n");
        out.append(indent).append(m.getName()).append('(');
        Parameter[] params = m.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(paramName(params[i], i)).append(": ").append(tsType(params[i].getType()));
        }
        out.append("): ").append(tsType(m.getReturnType())).append(";\n");
    }

    private static GraalFunctionDescription describe(KitDefinition def, Method m) {
        GraalFunctionDescription d = new GraalFunctionDescription();
        for (GraalFunctionDescription f : def.describeFunctions()) {
            if (f.getName().equals(m.getName())) {
                return f;
            }
        }
        return d;
    }

    private static String paramName(Parameter p, int index) {
        return p.isNamePresent() ? p.getName() : "arg" + index;
    }

    private static String tsType(Class<?> type) {
        if (type == void.class || type == Void.class) {
            return "void";
        }
        if (type == String.class || type == java.util.Date.class) {
            return "string";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (Number.class.isAssignableFrom(type) || type == int.class || type == long.class
                || type == double.class || type == float.class || type == short.class || type == byte.class) {
            return "number";
        }
        if (type == java.util.Map.class) {
            return "Record<string, unknown>";
        }
        if (type == java.util.List.class) {
            return "unknown[]";
        }
        return "unknown";
    }

    private static String comment(String text) {
        return text == null ? "" : text.replace("*/", "*／").replace('\n', ' ');
    }
}
