package cn.geelato.web.platform.logging.es;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class EsLogIndexRouter {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public String route(String loggerName, String prefix) {
        String category = "app";
        if (loggerName != null) {
            if (loggerName.startsWith("cn.geelato.web.platform.boot.interceptor")) {
                category = "request";
            } else if (loggerName.startsWith("cn.geelato.web.common.interceptor")) {
                category = "interceptor";
            } else if (loggerName.startsWith("cn.geelato.auth")) {
                category = "auth";
            } else if (loggerName.startsWith("cn.geelato.message")) {
                category = "message";
            } else if (loggerName.startsWith("cn.geelato.schedule")) {
                category = "schedule";
            }
        }
        String indexPrefix = (prefix == null || prefix.isBlank()) ? "geelato-log-" : prefix;
        return indexPrefix + category + "-" + LocalDate.now().format(DATE_FORMATTER);
    }
}
