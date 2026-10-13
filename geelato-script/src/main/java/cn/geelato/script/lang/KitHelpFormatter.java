package cn.geelato.script.lang;

import cn.geelato.core.graal.GraalFunctionDescription;
import cn.geelato.script.kit.KitDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * $gl.help() 文本输出：kit 命名空间清单（概览）与单 kit 方法详情（example + description）。
 */
public final class KitHelpFormatter {

    private KitHelpFormatter() {
    }

    public static String format(Iterable<KitDefinition> definitions, Predicate<String> namespaceAllowed, String namespace) {
        List<KitDefinition> visible = new ArrayList<>();
        for (KitDefinition def : definitions) {
            if (namespaceAllowed.test(def.getName())) {
                visible.add(def);
            }
        }
        if (namespace != null && !namespace.isBlank()) {
            return detail(visible, namespace.trim());
        }
        return overview(visible);
    }

    private static String overview(List<KitDefinition> visible) {
        StringBuilder sb = new StringBuilder();
        sb.append("kit 速查（$gl.help('<name>') 查看单 kit 详情）\n");
        List<KitDefinition> built = new ArrayList<>();
        List<KitDefinition> topLevel = new ArrayList<>();
        for (KitDefinition def : visible) {
            (def.isBuilt() ? built : topLevel).add(def);
        }
        for (KitDefinition def : built) {
            appendKitHead(sb, def.exposeName(), def);
        }
        if (!topLevel.isEmpty()) {
            sb.append("顶层 kit（直接以变量名访问）\n");
            for (KitDefinition def : topLevel) {
                appendKitHead(sb, def.exposeName(), def);
            }
        }
        return sb.toString();
    }

    private static void appendKitHead(StringBuilder sb, String access, KitDefinition def) {
        sb.append(access).append(" — ").append(def.getDescription()).append('\n');
        List<String> names = new ArrayList<>();
        for (GraalFunctionDescription fn : def.describeFunctions()) {
            names.add(fn.getName());
        }
        if (!names.isEmpty()) {
            sb.append("  ").append(String.join(" / ", names)).append('\n');
        }
    }

    private static String detail(List<KitDefinition> visible, String namespace) {
        for (KitDefinition def : visible) {
            if (!def.getName().equals(namespace)) {
                continue;
            }
            String access = def.exposeName();
            StringBuilder sb = new StringBuilder();
            sb.append(access).append(" — ").append(def.getDescription()).append('\n');
            for (GraalFunctionDescription fn : def.describeFunctions()) {
                sb.append("  ").append(fn.getName()).append('\n');
                if (fn.getExample() != null && !fn.getExample().isBlank()) {
                    sb.append("    示例: ").append(fn.getExample()).append('\n');
                }
                if (fn.getDescription() != null && !fn.getDescription().isBlank()) {
                    sb.append("    ").append(fn.getDescription()).append('\n');
                }
            }
            return sb.toString();
        }
        List<String> names = new ArrayList<>();
        for (KitDefinition def : visible) {
            names.add(def.getName());
        }
        return "未找到 kit: " + namespace + (names.isEmpty() ? "（当前无可见 kit）" : "（可用: " + String.join(", ", names) + "）");
    }
}
