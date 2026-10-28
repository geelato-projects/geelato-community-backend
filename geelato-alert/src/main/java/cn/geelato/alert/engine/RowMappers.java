package cn.geelato.alert.engine;

import cn.geelato.alert.entity.FmsAlert;
import cn.geelato.alert.entity.FmsAlertRule;
import cn.geelato.alert.entity.FmsAlertSilence;

import java.util.Date;
import java.util.Map;

/** 原生行 → 实体转换（引擎自用；键名兼容 snake_case / camelCase）。 */
public final class RowMappers {

    private RowMappers() {
    }

    public static FmsAlertRule toRule(Map<String, Object> row) {
        FmsAlertRule r = new FmsAlertRule();
        r.setId(str(row.get("id")));
        r.setAlertCode(str(row.get("alertCode")));
        r.setAlertName(str(row.get("alertName")));
        r.setMatchJson(str(row.get("matchJson")));
        r.setTriggerMode(str(row.get("triggerMode")));
        r.setCronExpression(str(row.get("cronExpression")));
        r.setDatasetJson(str(row.get("datasetJson")));
        r.setAggregateMode(str(row.get("aggregateMode")));
        r.setGroupWaitSeconds(intObj(row.get("groupWaitSeconds")));
        r.setGroupIntervalSeconds(intObj(row.get("groupIntervalSeconds")));
        r.setRepeatIntervalMinutes(intObj(row.get("repeatIntervalMinutes")));
        r.setMaxItems(intObj(row.get("maxItems")));
        r.setResolveNotify(intVal(row.get("resolveNotify")));
        r.setReceiverJson(str(row.get("receiverJson")));
        r.setRouteJson(str(row.get("routeJson")));
        r.setDailyLimit(intObj(row.get("dailyLimit")));
        r.setEnableStatus(intVal(row.get("enableStatus")));
        r.setVersion(intVal(row.get("version")));
        r.setAppId(str(row.get("appId")));
        r.setTenantCode(str(row.get("tenantCode")));
        r.setDelStatus(intVal(row.get("delStatus")));
        return r;
    }

    public static FmsAlert toAlert(Map<String, Object> row) {
        FmsAlert r = new FmsAlert();
        r.setId(str(row.get("id")));
        r.setAlertCode(str(row.get("alertCode")));
        r.setAlertName(str(row.get("alertName")));
        r.setAlertVersion(intObj(row.get("alertVersion")));
        r.setTriggerType(str(row.get("triggerType")));
        r.setEventType(str(row.get("eventType")));
        r.setBatchNo(str(row.get("batchNo")));
        r.setGroupKey(str(row.get("groupKey")));
        r.setActiveGroupKey(str(row.get("activeGroupKey")));
        r.setEventCount(intVal(row.get("eventCount")));
        r.setEventIds(str(row.get("eventIds")));
        r.setSourceJson(str(row.get("sourceJson")));
        r.setStatsJson(str(row.get("statsJson")));
        r.setRenderedTitle(str(row.get("renderedTitle")));
        r.setRenderedContent(str(row.get("renderedContent")));
        r.setReceiverIds(str(row.get("receiverIds")));
        r.setReceiverNames(str(row.get("receiverNames")));
        r.setChannels(str(row.get("channels")));
        r.setStatus(str(row.get("status")));
        r.setSilenceId(str(row.get("silenceId")));
        r.setFlushAt(toDate(row.get("flushAt")));
        r.setFirstEventAt(toDate(row.get("firstEventAt")));
        r.setLastEventAt(toDate(row.get("lastEventAt")));
        r.setLastSentAt(toDate(row.get("lastSentAt")));
        r.setBizKey(str(row.get("bizKey")));
        r.setRetryCount(intVal(row.get("retryCount")));
        r.setErrorMessage(str(row.get("errorMessage")));
        r.setSentAt(toDate(row.get("sentAt")));
        r.setAppId(str(row.get("appId")));
        r.setTenantCode(str(row.get("tenantCode")));
        r.setDelStatus(intVal(row.get("delStatus")));
        return r;
    }

    public static FmsAlertSilence toSilence(Map<String, Object> row) {
        FmsAlertSilence s = new FmsAlertSilence();
        s.setId(str(row.get("id")));
        s.setMatchJson(str(row.get("matchJson")));
        s.setWindowType(str(row.get("windowType")));
        s.setStartAt(toDate(row.get("startAt")));
        s.setEndAt(toDate(row.get("endAt")));
        s.setTimeWindow(str(row.get("timeWindow")));
        s.setAllowResolve(intVal(row.get("allowResolve")));
        s.setStatus(str(row.get("status")));
        s.setTenantCode(str(row.get("tenantCode")));
        s.setDelStatus(intVal(row.get("delStatus")));
        return s;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int intVal(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Integer intObj(Object o) {
        return o == null ? null : intVal(o);
    }

    private static Date toDate(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Date d) {
            return d;
        }
        if (o instanceof java.time.LocalDateTime ldt) {
            return Date.from(ldt.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        if (o instanceof java.time.Instant instant) {
            return Date.from(instant);
        }
        return null;
    }
}
