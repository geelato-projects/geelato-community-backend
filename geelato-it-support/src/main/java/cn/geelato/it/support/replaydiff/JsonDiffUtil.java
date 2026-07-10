package cn.geelato.it.support.replaydiff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JsonDiffUtil {
    private JsonDiffUtil() {
    }

    public static JsonNode prune(JsonNode node, DiffIgnoreConfig ignoreConfig) {
        return prune(node, "", ignoreConfig);
    }

    public static List<JsonDiffEntry> diff(JsonNode left, JsonNode right, DiffIgnoreConfig ignoreConfig, int maxEntries) {
        List<JsonDiffEntry> out = new ArrayList<>();
        diff("", normalize(left), normalize(right), ignoreConfig, out, maxEntries);
        return out;
    }

    private static JsonNode prune(JsonNode node, String path, DiffIgnoreConfig ignoreConfig) {
        JsonNode normalized = normalize(node);
        if (ignoreConfig != null && ignoreConfig.shouldIgnore(path.isEmpty() ? "/" : path)) {
            return MissingNode.getInstance();
        }
        if (normalized.isObject()) {
            ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> it = normalized.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String childPath = path + "/" + escape(entry.getKey());
                if (ignoreConfig != null && ignoreConfig.shouldIgnore(childPath)) {
                    continue;
                }
                JsonNode child = prune(entry.getValue(), childPath, ignoreConfig);
                if (!child.isMissingNode()) {
                    objectNode.set(entry.getKey(), child);
                }
            }
            return objectNode;
        }
        if (normalized.isArray()) {
            ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
            for (int i = 0; i < normalized.size(); i++) {
                String childPath = path + "/" + i;
                if (ignoreConfig != null && ignoreConfig.shouldIgnore(childPath)) {
                    continue;
                }
                JsonNode child = prune(normalized.get(i), childPath, ignoreConfig);
                if (!child.isMissingNode()) {
                    arrayNode.add(child);
                }
            }
            return arrayNode;
        }
        return normalized;
    }

    private static void diff(String path, JsonNode left, JsonNode right, DiffIgnoreConfig ignoreConfig, List<JsonDiffEntry> out, int maxEntries) {
        if (out.size() >= maxEntries) {
            return;
        }
        String pointer = path.isEmpty() ? "/" : path;
        if (ignoreConfig != null && ignoreConfig.shouldIgnore(pointer)) {
            return;
        }

        boolean leftMissing = left.isMissingNode();
        boolean rightMissing = right.isMissingNode();

        if (leftMissing && rightMissing) {
            return;
        }
        if (leftMissing) {
            out.add(new JsonDiffEntry(pointer, "ADDED", null, stringify(right)));
            return;
        }
        if (rightMissing) {
            out.add(new JsonDiffEntry(pointer, "REMOVED", stringify(left), null));
            return;
        }
        if (left.equals(right)) {
            return;
        }

        if (left.isObject() && right.isObject()) {
            Set<String> fieldNames = new LinkedHashSet<>();
            left.fieldNames().forEachRemaining(fieldNames::add);
            right.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                diff(path + "/" + escape(fieldName), normalize(left.get(fieldName)), normalize(right.get(fieldName)), ignoreConfig, out, maxEntries);
                if (out.size() >= maxEntries) {
                    return;
                }
            }
            return;
        }

        if (left.isArray() && right.isArray()) {
            int max = Math.max(left.size(), right.size());
            for (int i = 0; i < max; i++) {
                diff(path + "/" + i, normalize(left.path(i)), normalize(right.path(i)), ignoreConfig, out, maxEntries);
                if (out.size() >= maxEntries) {
                    return;
                }
            }
            return;
        }

        out.add(new JsonDiffEntry(pointer, "CHANGED", stringify(left), stringify(right)));
    }

    private static JsonNode normalize(JsonNode node) {
        return node == null ? MissingNode.getInstance() : node;
    }

    private static String stringify(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String text = node.toString();
        if (text.length() <= 2000) {
            return text;
        }
        return text.substring(0, 2000);
    }

    private static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }
}
