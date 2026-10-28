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
    SWAGGER("swagger"),
    APP("app"),
    SCHEDULE("schedule"),
    SQL("sql"),
    SCRIPT("script"),
    SITE("site"),
    EXPORT("export"),
    MODEL("model"),
    DDL("ddl");

    private final String id;

    AppScaffoldCapability(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /**
     * 脚手架默认（内置）能力集合：凡下游基于 scaffold 启动的应用，默认即暴露这些
     * 平台端点，无需再由下游通过 extra-controllers 逐条声明——能力边界由脚手架自身决定。
     * 准入硬边界交给 {@code DesignTimeRoleInterceptor} 等鉴权拦截器，不靠裁剪端点兜底。
     */
    public static List<AppScaffoldCapability> builtinCapabilities() {
        return List.of(
                LOGIN,
                MQL,
                ORGANIZATION,
                USER,
                DICTIONARY,
                NOTICE,
                UPLOAD,
                APP,
                SCHEDULE,
                SQL,
                SCRIPT,
                SITE,
                EXPORT,
                MODEL,
                DDL
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
