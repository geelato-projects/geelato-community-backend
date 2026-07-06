package cn.geelato.app.scaffold.boot;

import java.util.Locale;

public enum AppScaffoldCapability {
    LOGIN("login"),
    MQL("mql"),
    ORGANIZATION("organization"),
    USER("user"),
    DICTIONARY("dictionary"),
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

