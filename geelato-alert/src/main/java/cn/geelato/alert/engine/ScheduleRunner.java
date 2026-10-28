package cn.geelato.alert.engine;

import cn.geelato.alert.engine.dataset.DatasetExecutor;
import cn.geelato.alert.engine.event.AlertGrouper;
import cn.geelato.alert.engine.support.AlertContextExecutor;
import cn.geelato.alert.entity.FmsAlert;
import cn.geelato.alert.entity.FmsAlertRule;
import cn.geelato.core.orm.Dao;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * SCHEDULE 定时执行（P-A1 SCHEDULE 入口）：cron 到期（基线=该规则最近一次 SCHEDULE 记录时刻，
 * missed fire 只补一次）→ 数据集执行 → condition 命中落 PENDING（stats_json 存 dataset 快照，
 * 冲刷渲染时恢复为 $ctx.dataset）。
 */
@Slf4j
public class ScheduleRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LocalDateTime EPOCH_BASE = LocalDateTime.of(2020, 1, 1, 0, 0);

    private final Dao dao;
    private final DatasetExecutor datasetExecutor;

    public ScheduleRunner(@Qualifier("dynamicDao") Dao dao, DatasetExecutor datasetExecutor) {
        this.dao = dao;
        this.datasetExecutor = datasetExecutor;
    }

    public void tick() {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT * FROM platform_alert_rule WHERE enable_status = 1 AND del_status = 0 "
                        + "AND trigger_mode IN ('SCHEDULE','BOTH') AND cron_expression IS NOT NULL "
                        + "AND cron_expression <> ''",
                new Object[]{});
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> row : rows) {
            try {
                FmsAlertRule rule = RowMappers.toRule(row);
                if (isDue(rule, now) && !hasPendingSchedule(rule)) {
                    execute(rule);
                }
            } catch (Exception e) {
                log.warn("SCHEDULE 执行异常 rule={}: {}", row.get("alertCode"), e.getMessage());
            }
        }
    }

    /** 到期判定：next(lastRun) <= now；lastRun 缺失视为立即可跑。 */
    private boolean isDue(FmsAlertRule rule, LocalDateTime now) {
        try {
            CronExpression cron = CronExpression.parse(rule.getCronExpression().trim());
            LocalDateTime base = lastRunAt(rule);
            LocalDateTime next = cron.next(base == null ? EPOCH_BASE : base);
            return next != null && !next.isAfter(now);
        } catch (Exception e) {
            log.warn("cron 解析失败 rule={} cron={}", rule.getAlertCode(), rule.getCronExpression());
            return false;
        }
    }

    private LocalDateTime lastRunAt(FmsAlertRule rule) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT MAX(create_at) AS last_at FROM platform_alert WHERE alert_code = ? AND tenant_code = ? "
                        + "AND trigger_type = 'SCHEDULE'",
                new Object[]{rule.getAlertCode(), rule.getTenantCode()});
        if (rows != null && !rows.isEmpty()) {
            Object v = rows.get(0).containsKey("last_at") ? rows.get(0).get("last_at") : rows.get(0).get("lastAt");
            if (v instanceof LocalDateTime ldt) {
                return ldt;
            }
            if (v instanceof java.util.Date d) {
                return LocalDateTime.ofInstant(d.toInstant(), java.time.ZoneId.systemDefault());
            }
        }
        return null;
    }

    /** 防重：已有未完成 SCHEDULE 记录（PENDING/READY）跳过。 */
    private boolean hasPendingSchedule(FmsAlertRule rule) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT id FROM platform_alert WHERE alert_code = ? AND tenant_code = ? "
                        + "AND trigger_type = 'SCHEDULE' AND status IN ('PENDING','READY') AND del_status = 0 LIMIT 1",
                new Object[]{rule.getAlertCode(), rule.getTenantCode()});
        return rows != null && !rows.isEmpty();
    }

    private void execute(FmsAlertRule rule) {
        DatasetExecutor.Outcome outcome = datasetExecutor.execute(rule);
        if (!outcome.isConditionHit()) {
            log.debug("SCHEDULE 数据集条件未命中，不落记录 rule={}", rule.getAlertCode());
            return;
        }
        Map<String, Object> dataset = outcome.getDataset();
        List<String> rowIds = rowInstanceIds(dataset);
        Date now = new Date();
        FmsAlert record = new FmsAlert();
        record.setAlertCode(rule.getAlertCode());
        record.setAlertName(rule.getAlertName());
        record.setAlertVersion(rule.getVersion());
        record.setTriggerType("SCHEDULE");
        record.setGroupKey(rule.getAlertCode());
        record.setEventCount(intOf(dataset.get("total")));
        record.setEventIds(AlertGrouper.writeIds(rowIds));
        record.setStatsJson(writeJson(dataset));
        record.setStatus("PENDING");
        record.setFirstEventAt(now);
        record.setLastEventAt(now);
        record.setFlushAt(now);
        record.setAppId(rule.getAppId());
        record.setTenantCode(rule.getTenantCode());
        AlertGrouper.fillAudit(record, now);
        AlertContextExecutor.withContext(rule.getTenantCode(), () -> cn.geelato.orm.MetaFactory.save(record));
        log.info("SCHEDULE 到期落记录 rule={} total={} hit=true", rule.getAlertCode(), dataset.get("total"));
    }

    /** 清单行实例 id（stats 行无 id，取 rows[].id）。 */
    @SuppressWarnings("unchecked")
    private static List<String> rowInstanceIds(Map<String, Object> dataset) {
        List<String> ids = new ArrayList<>();
        Object rows = dataset.get("rows");
        if (rows instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row && row.get("id") != null) {
                    ids.add(String.valueOf(row.get("id")));
                }
            }
        }
        return ids;
    }

    private static int intOf(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("dataset 序列化失败", e);
        }
    }
}
