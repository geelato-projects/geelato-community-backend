package cn.geelato.app.scaffold.boot;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.List;

public enum AppScaffoldCapability {
    LOGIN("login"),
    MQL("mql"),
    ORGANIZATION("organization"),
    USER("user"),
    DICTIONARY("dictionary"),
    NOTICE("notice"),
    UPLOAD("upload"),
    DOWNLOAD("download"),
    OSS("oss"),
    SWAGGER("swagger");

    private final String id;

    AppScaffoldCapability(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static List<AppScaffoldCapability> builtinCapabilities() {
        return List.of(
                LOGIN,
                MQL,
                ORGANIZATION,
                USER,
                DICTIONARY,
                NOTICE,
                UPLOAD
        );
    }

    public static List<String> builtinCapabilityIds() {
        return builtinCapabilities().stream()
                .map(AppScaffoldCapability::id)
                .collect(Collectors.toList());
    }

    public static AppScaffoldCapability fromId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (AppScaffoldCapability capability : values()) {
            if (capability.id.equals(normalized)) {
                return capability;
            }
        }
        return null;
    }
}
