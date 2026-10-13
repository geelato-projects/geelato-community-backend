package cn.geelato.exception.engine.lifecycle;

import cn.geelato.exception.engine.participant.Participant;
import cn.geelato.exception.engine.support.EngineContextExecutor;
import cn.geelato.exception.entity.FmsException;
import cn.geelato.exception.entity.FmsExceptionHandle;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实例生命周期持久化原语：指纹 upsert（一人一实例，在途唯一）、组级 AUTO_RECOVER、
 * handle 只追加流水、notify 待发行、生命周期事件发布。
 * 供命中提交链与事件复评共用；调用方负责线程上下文（扫描/事件线程经 EngineContextExecutor 重建）。
 */
@Slf4j
public class InstanceLifecycleService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectProvider<ApplicationEventPublisher> publisherProvider;

    public InstanceLifecycleService(ObjectProvider<ApplicationEventPublisher> publisherProvider) {
        this.publisherProvider = publisherProvider;
    }

    /** 命中提交：指纹在途续期 / 不在途新建实例 + CREATE 流水 + CREATED 触达（受规则 notifyLevel 门槛）。 */
    public String upsertInstance(String ruleCode, String ruleName, String domainCode, String level,
                                 Map<String, Object> hit, Participant handler, int ruleVersion, String appId) {
        String bizType = str(hit.get("bizType"));
        String bizId = str(hit.get("bizId"));
        String sourceKey = ruleCode + "|" + bizType + "|" + bizId;
        String fingerprint = sourceKey + "|" + handler.getId();
        FmsException existing = findActiveByFingerprint(fingerprint);
        Date now = new Date();
        if (existing != null) {
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("id", existing.getId());
            patch.put("lastSeenAt", now);
            patch.put("hitCount", existing.getHitCount() + 1);
            patch.put("hitBrief", briefOf(hit, existing.getHitBrief()));
            patch.put("detailJson", detailOf(hit, existing.getDetailJson()));
            patch.put("updateAt", now);
            patch.put("updater", EngineContextExecutor.SYSTEM_USER_ID);
            patch.put("updaterName", EngineContextExecutor.SYSTEM_USER_NAME);
            MetaFactory.save(patchInstance(patch));
            return "renewed";
        }
        FmsException exception = new FmsException();
        exception.setRuleCode(ruleCode);
        exception.setRuleName(ruleName);
        exception.setDomainCode(domainCode);
        exception.setAppId(appId);
        exception.setLevel(levelOverride(hit, level));
        exception.setActiveFingerprint(fingerprint);
        exception.setSourceKey(sourceKey);
        exception.setParticipantId(handler.getId());
        exception.setParticipantName(handler.getName());
        exception.setParticipantOrg(handler.getOrgId());
        exception.setBizType(bizType);
        exception.setBizId(bizId);
        exception.setBizNo(str(hit.get("bizNo")));
        exception.setCustomerId(str(hit.get("customerId")));
        exception.setCustomerName(str(hit.get("customerName")));
        exception.setStatus("OPEN");
        exception.setFirstAt(now);
        exception.setLastSeenAt(now);
        exception.setHitCount(1);
        exception.setAnchorElapsed(anchorElapsed(hit));
        exception.setHitBrief(briefOf(hit, null));
        exception.setDetailJson(detailOf(hit, null));
        exception.setRuleVersion(ruleVersion);
        fillAudit(exception, now);
        try {
            String savedId = MetaFactory.save(exception);
            if (exception.getId() == null && savedId != null) {
                exception.setId(savedId);
            }
        } catch (Exception e) {
            // 唯一索引兜底并发：同指纹他人已插入，转续期
            FmsException race = findActiveByFingerprint(fingerprint);
            if (race != null) {
                return "renewed";
            }
            throw e;
        }
        appendHandle(exception.getId(), "CREATE", null, "OPEN", hit);
        publish("EXC_CREATED", exception.getId(), ruleCode, sourceKey, handler.getId());
        return "created";
    }

    /** 组级恢复：source_key 内 OPEN/PROCESSING 全部 AUTO 关闭、指纹置 NULL、流水留痕（ESCALATED 不动）。 */
    public int recoverSourceGroup(String ruleCode, String sourceKey, String reason) {
        int recovered = 0;
        Date now = new Date();
        for (FmsException exception : listActiveBySourceKey(sourceKey)) {
            if (!ruleCode.equals(exception.getRuleCode())) {
                continue;
            }
            if (!"OPEN".equals(exception.getStatus()) && !"PROCESSING".equals(exception.getStatus())) {
                continue;
            }
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("id", exception.getId());
            patch.put("status", "RESOLVED");
            patch.put("resolvedAt", now);
            patch.put("resolvedType", "AUTO");
            patch.put("activeFingerprint", null);
            patch.put("updateAt", now);
            patch.put("updater", EngineContextExecutor.SYSTEM_USER_ID);
            patch.put("updaterName", EngineContextExecutor.SYSTEM_USER_NAME);
            MetaFactory.save(patchInstance(patch));
            appendHandle(exception.getId(), "AUTO_RECOVER", exception.getStatus(), "RESOLVED",
                    Map.of("reason", reason == null ? "复评不命中，组级恢复" : reason));
            publish("EXC_RECOVERED", exception.getId(), ruleCode, sourceKey, exception.getParticipantId());
            recovered++;
        }
        return recovered;
    }

    public List<FmsException> listActiveBySourceKey(String sourceKey) {
        List<Map<String, Object>> rows = MetaFactory.query(FmsException.class)
                .where(Filter.eq("sourceKey", sourceKey), Filter.eq("delStatus", 0))
                .list();
        return toEntities(rows);
    }

    public FmsException findActiveByFingerprint(String fingerprint) {
        List<Map<String, Object>> rows = MetaFactory.query(FmsException.class)
                .where(Filter.eq("activeFingerprint", fingerprint), Filter.eq("delStatus", 0))
                .list();
        List<FmsException> entities = toEntities(rows);
        for (FmsException e : entities) {
            if ("OPEN".equals(e.getStatus()) || "PROCESSING".equals(e.getStatus())) {
                return e;
            }
        }
        return null;
    }


    public void appendHandle(String exceptionId, String action, String beforeStatus, String afterStatus, Map<String, Object> payload) {
        FmsExceptionHandle handle = new FmsExceptionHandle();
        handle.setExceptionId(exceptionId);
        handle.setAction(action);
        handle.setOperatorType("SYSTEM");
        handle.setOperatorId(EngineContextExecutor.SYSTEM_USER_ID);
        handle.setOperatorName(EngineContextExecutor.SYSTEM_USER_NAME);
        handle.setBeforeStatus(beforeStatus);
        handle.setAfterStatus(afterStatus);
        handle.setRemark(payload == null ? null : truncate(writeJson(payload), 1000));
        fillAudit(handle, new Date());
        MetaFactory.save(handle);
    }

    private void publish(String eventType, String exceptionId, String ruleCode, String sourceKey, String participantId) {
        try {
            ApplicationEventPublisher publisher = publisherProvider == null ? null : publisherProvider.getIfAvailable();
            if (publisher != null) {
                publisher.publishEvent(new ExceptionLifecycleEvent(this, eventType, exceptionId, ruleCode, sourceKey, participantId));
            }
        } catch (Exception e) {
            log.warn("生命周期事件发布失败 {} {}: {}", eventType, exceptionId, e.getMessage());
        }
    }

    private List<FmsException> toEntities(List<Map<String, Object>> rows) {
        List<FmsException> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            FmsException e = new FmsException();
            e.setId(str(row.get("id")));
            e.setRuleCode(str(row.get("ruleCode")));
            e.setRuleName(str(row.get("ruleName")));
            e.setDomainCode(str(row.get("domainCode")));
            e.setLevel(str(row.get("level")));
            e.setSourceKey(str(row.get("sourceKey")));
            e.setActiveFingerprint(str(row.get("activeFingerprint")));
            e.setParticipantId(str(row.get("participantId")));
            e.setParticipantName(str(row.get("participantName")));
            e.setBizType(str(row.get("bizType")));
            e.setBizId(str(row.get("bizId")));
            e.setStatus(str(row.get("status")));
            e.setHitCount(row.get("hitCount") instanceof Number n ? n.intValue() : 0);
            e.setHitBrief(str(row.get("hitBrief")));
            e.setDetailJson(str(row.get("detailJson")));
            e.setFirstAt(toDate(row.get("firstAt")));
            e.setLastSeenAt(toDate(row.get("lastSeenAt")));
            e.setResolvedAt(toDate(row.get("resolvedAt")));
            list.add(e);
        }
        return list;
    }

    private FmsException patchInstance(Map<String, Object> patch) {
        FmsException e = new FmsException();
        e.setId((String) patch.get("id"));
        e.setLastSeenAt((Date) patch.get("lastSeenAt"));
        e.setHitCount((Integer) patch.get("hitCount"));
        e.setHitBrief((String) patch.get("hitBrief"));
        e.setDetailJson((String) patch.get("detailJson"));
        e.setUpdateAt((Date) patch.get("updateAt"));
        e.setUpdater((String) patch.get("updater"));
        e.setUpdaterName((String) patch.get("updaterName"));
        return e;
    }

    private static void fillAudit(Object entity, Date now) {
        if (entity instanceof FmsException e) {
            e.setTenantCode(currentTenant());
            e.setDelStatus(0);
            e.setCreateAt(now);
            e.setUpdateAt(now);
            e.setCreator(EngineContextExecutor.SYSTEM_USER_ID);
            e.setCreatorName(EngineContextExecutor.SYSTEM_USER_NAME);
        } else if (entity instanceof FmsExceptionHandle h) {
            h.setTenantCode(currentTenant());
            h.setDelStatus(0);
            h.setCreateAt(now);
            h.setUpdateAt(now);
            h.setCreator(EngineContextExecutor.SYSTEM_USER_ID);
            h.setCreatorName(EngineContextExecutor.SYSTEM_USER_NAME);
        }
    }

    private static String currentTenant() {
        try {
            return cn.geelato.exception.support.SessionCtxHelper.getCurrentTenantCode();
        } catch (Exception e) {
            return "geelato";
        }
    }

    private static String levelOverride(Map<String, Object> hit, String ruleLevel) {
        Object v = hit.get("levelOverride");
        return v == null || String.valueOf(v).isBlank() ? ruleLevel : String.valueOf(v);
    }

    private static String briefOf(Map<String, Object> hit, String fallback) {
        Object v = hit.get("hitBrief");
        return v == null || String.valueOf(v).isBlank() ? fallback : truncate(String.valueOf(v), 500);
    }

    private static String detailOf(Map<String, Object> hit, String fallback) {
        Object snapshot = hit.get("snapshot");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("hitBrief", hit.get("hitBrief"));
        detail.put("snapshot", snapshot);
        String json = writeJson(detail);
        return "{}".equals(json) && fallback != null ? fallback : json;
    }

    /** 锚点时间兼容格式：常规 yyyy-MM-dd HH:mm:ss 与 ISO 本地时间。 */
    private static final java.time.format.DateTimeFormatter[] ANCHOR_TIME_FORMATS = {
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME};

    private static Long anchorElapsed(Map<String, Object> hit) {
        Object anchorTime = hit.get("anchorTime");
        if (anchorTime == null) {
            return null;
        }
        String text = String.valueOf(anchorTime);
        for (java.time.format.DateTimeFormatter f : ANCHOR_TIME_FORMATS) {
            try {
                java.time.LocalDateTime t = java.time.LocalDateTime.parse(text, f);
                return java.time.Duration.between(t, java.time.LocalDateTime.now()).getSeconds();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Date toDate(Object o) {
        if (o instanceof Date d) {
            return d;
        }
        if (o instanceof java.time.LocalDateTime ldt) {
            return java.util.Date.from(ldt.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        return null;
    }
}
