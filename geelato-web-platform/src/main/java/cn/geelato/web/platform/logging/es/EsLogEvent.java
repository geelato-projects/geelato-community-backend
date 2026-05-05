package cn.geelato.web.platform.logging.es;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class EsLogEvent {
    private final Instant timestamp;
    private final String level;
    private final String logger;
    private final String thread;
    private final String message;
    private final Map<String, String> mdc;
    private final Map<String, Object> extra;

    public EsLogEvent(Instant timestamp, String level, String logger, String thread, String message,
                      Map<String, String> mdc, Map<String, Object> extra) {
        this.timestamp = timestamp;
        this.level = level;
        this.logger = logger;
        this.thread = thread;
        this.message = message;
        this.mdc = mdc;
        this.extra = extra;
    }

    public String loggerName() {
        return logger == null ? "" : logger;
    }

    public Map<String, Object> toDocument(String appName, String envName) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("@timestamp", timestamp);
        doc.put("level", level);
        doc.put("logger", logger);
        doc.put("thread", thread);
        doc.put("message", message);
        doc.put("app", appName);
        doc.put("env", envName);
        if (mdc != null) {
            doc.putAll(mdc);
        }
        if (extra != null) {
            doc.putAll(extra);
        }
        return doc;
    }
}
