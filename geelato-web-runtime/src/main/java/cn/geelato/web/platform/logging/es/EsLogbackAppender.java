package cn.geelato.web.platform.logging.es;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class EsLogbackAppender extends AppenderBase<ILoggingEvent> {
    @Override
    protected void append(ILoggingEvent eventObject) {
        if (eventObject == null) {
            return;
        }
        Map<String, String> mdcMap = eventObject.getMDCPropertyMap();
        Map<String, String> copiedMdc = mdcMap == null ? Map.of() : new LinkedHashMap<>(mdcMap);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("formattedMessage", eventObject.getFormattedMessage());
        extra.put("loggerContext", eventObject.getLoggerContextVO().getName());

        EsLogEvent event = new EsLogEvent(
                Instant.ofEpochMilli(eventObject.getTimeStamp()),
                String.valueOf(eventObject.getLevel()),
                eventObject.getLoggerName(),
                eventObject.getThreadName(),
                eventObject.getMessage(),
                copiedMdc,
                extra
        );
        EsLogBuffer.offer(event);
    }
}
