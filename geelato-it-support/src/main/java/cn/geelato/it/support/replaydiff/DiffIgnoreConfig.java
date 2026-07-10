package cn.geelato.it.support.replaydiff;

import cn.geelato.it.support.json.ObjectMappers;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class DiffIgnoreConfig {
    private final Set<String> ignorePointers;
    private final List<Pattern> ignorePointerRegex;

    private DiffIgnoreConfig(Set<String> ignorePointers, List<Pattern> ignorePointerRegex) {
        this.ignorePointers = Collections.unmodifiableSet(new HashSet<>(ignorePointers));
        this.ignorePointerRegex = Collections.unmodifiableList(new ArrayList<>(ignorePointerRegex));
    }

    public static DiffIgnoreConfig empty() {
        return new DiffIgnoreConfig(Set.of(), List.of());
    }

    public static DiffIgnoreConfig ofPointers(List<String> pointers) {
        if (pointers == null || pointers.isEmpty()) {
            return empty();
        }
        return new DiffIgnoreConfig(new HashSet<>(pointers), List.of());
    }

    public static DiffIgnoreConfig load(Path path) {
        if (path == null || !Files.exists(path)) {
            return empty();
        }
        try {
            JsonNode root = ObjectMappers.defaultMapper().readTree(Files.readAllBytes(path));
            return parse(root);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load ignore config: " + path, e);
        }
    }

    public boolean shouldIgnore(String jsonPointer) {
        if (jsonPointer == null) {
            return false;
        }
        if (ignorePointers.contains(jsonPointer)) {
            return true;
        }
        for (Pattern pattern : ignorePointerRegex) {
            if (pattern.matcher(jsonPointer).matches()) {
                return true;
            }
        }
        return false;
    }

    public Set<String> ignorePointers() {
        return ignorePointers;
    }

    public List<Pattern> ignorePointerRegex() {
        return ignorePointerRegex;
    }

    private static DiffIgnoreConfig parse(JsonNode root) {
        if (root == null || root.isNull()) {
            return empty();
        }
        if (root.isArray()) {
            List<String> pointers = new ArrayList<>();
            for (JsonNode node : root) {
                if (node != null && node.isTextual()) {
                    pointers.add(node.asText());
                }
            }
            return ofPointers(pointers);
        }
        if (!root.isObject()) {
            return empty();
        }

        Set<String> pointers = new HashSet<>();
        List<Pattern> regex = new ArrayList<>();

        JsonNode pointerArray = root.get("ignorePointers");
        if (pointerArray != null && pointerArray.isArray()) {
            for (JsonNode node : pointerArray) {
                if (node != null && node.isTextual()) {
                    pointers.add(node.asText());
                }
            }
        }

        JsonNode regexArray = root.get("ignorePointerRegex");
        if (regexArray != null && regexArray.isArray()) {
            for (JsonNode node : regexArray) {
                if (node != null && node.isTextual()) {
                    regex.add(Pattern.compile(node.asText()));
                }
            }
        }

        return new DiffIgnoreConfig(pointers, regex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DiffIgnoreConfig that)) {
            return false;
        }
        return ignorePointers.equals(that.ignorePointers) && ignorePointerRegex.equals(that.ignorePointerRegex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ignorePointers, ignorePointerRegex);
    }
}
