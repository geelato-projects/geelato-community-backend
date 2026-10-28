package cn.geelato.alert.service;

import cn.geelato.alert.entity.FmsAlert;
import cn.geelato.alert.entity.FmsAlertRule;
import cn.geelato.alert.support.SessionCtxHelper;
import cn.geelato.core.orm.Dao;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.MetaQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警管理端点服务：手动触发 SCHEDULE 规则 / 手动冲刷聚合缓冲 / 发送统计 / 规则近 7 日发送量。
 *
 * <p>契约面 + 持久化深度：trigger 落 PENDING 缓冲记录留痕；flush 以 CAS 抢占 PENDING→READY
 * （引擎启用时由 FlushScanner 冲刷渲染 + DeliverEngine 投递消费）；stats 直查 platform_alert 聚合。
 *
 * <p>关联文档：bea/业务告警中心-接口设计-2026-09-22.md §6
 */
@Service
public class AlertAdminService {

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    @Autowired
    private AlertRuleService ruleService;

    /**
     * 手动触发（{@code POST /admin/trigger}）：仅 ENABLE 的 SCHEDULE/BOTH 规则；
     * 同链路执行留痕——真实数据集执行留后续，此处落一条 PENDING 缓冲记录。
     *
     * @return {triggered, recordId, reason}
     */
    public Map<String, Object> trigger(String alertCode, String appId) {
        Map<String, Object> result = new LinkedHashMap<>();
        FmsAlertRule rule = ruleService.getByCode(alertCode, appId);
        if (rule == null) {
            result.put("triggered", false);
            result.put("reason", "NOT_FOUND");
            return result;
        }
        if (rule.getEnableStatus() != 1) {
            result.put("triggered", false);
            result.put("reason", "RULE_DISABLED");
            return result;
        }
        if (!"SCHEDULE".equals(rule.getTriggerMode()) && !"BOTH".equals(rule.getTriggerMode())) {
            result.put("triggered", false);
            result.put("reason", "非 SCHEDULE/BOTH 规则不支持手动触发");
            return result;
        }
        FmsAlert record = new FmsAlert();
        record.setAlertCode(rule.getAlertCode());
        record.setAlertName(rule.getAlertName());
        record.setAlertVersion(rule.getVersion());
        record.setTriggerType("SCHEDULE");
        record.setStatus("PENDING");
        record.setBatchNo("MANUAL-" + System.currentTimeMillis());
        record.setFirstEventAt(new Date());
        record.setLastEventAt(new Date());
        record.setFlushAt(new Date());
        record.setAppId(rule.getAppId());
        fillNewAudit(record);
        String savedId = MetaFactory.save(record);
        String recordId = record.getId() != null ? record.getId() : savedId;
        result.put("triggered", true);
        result.put("recordId", recordId);
        result.put("reason", "已落 PENDING 缓冲（未执行数据集，渲染上下文为空）；引擎启用时由冲刷链路消费");
        return result;
    }

    /**
     * 手动冲刷（{@code POST /admin/flush}）：运维排障入口。CAS 抢占 PENDING→READY 防并发双冲刷；
     * 渲染 + 投递消费 READY 留后续。
     *
     * @return {flushedCount, note}
     */
    public Map<String, Object> flush(String alertCode, boolean all) {
        StringBuilder sql = new StringBuilder(
                "UPDATE platform_alert SET status = 'READY', update_at = NOW() "
                        + "WHERE status = 'PENDING' AND del_status = 0 AND tenant_code = ?");
        List<Object> args = new ArrayList<>();
        args.add(SessionCtxHelper.getCurrentTenantCode());
        if (!all && alertCode != null && !alertCode.isBlank()) {
            sql.append(" AND alert_code = ?");
            args.add(alertCode.trim());
        }
        int flushed = dynamicDao.nativeExecute(sql.toString(), args.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flushedCount", flushed);
        result.put("note", "缓冲已抢占转 READY（跳过冲刷渲染）；投递引擎以渲染兜底消费");
        return result;
    }

    /**
     * 发送统计（{@code GET /admin/stats}）：按 日 × 告警规则 × 触发类型 聚合记录量
     * （v_platform_alert_daily_stat 口径的表侧近似；message 侧 sent/fail 明细回查留后续）。
     */
    public List<Map<String, Object>> stats(String alertCode, Date from, Date to) {
        StringBuilder sql = new StringBuilder(
                "SELECT DATE(create_at) AS stat_date, alert_code, trigger_type, "
                        + "COUNT(*) AS record_count, "
                        + "SUM(CASE WHEN status = 'SENT' THEN 1 ELSE 0 END) AS sent_count, "
                        + "SUM(CASE WHEN status = 'FAIL' THEN 1 ELSE 0 END) AS fail_count, "
                        + "SUM(CASE WHEN status = 'SILENCED' THEN 1 ELSE 0 END) AS silenced_count "
                        + "FROM platform_alert WHERE del_status = 0 AND tenant_code = ?");
        List<Object> args = new ArrayList<>();
        args.add(SessionCtxHelper.getCurrentTenantCode());
        if (alertCode != null && !alertCode.isBlank()) {
            sql.append(" AND alert_code = ?");
            args.add(alertCode.trim());
        }
        if (from != null) {
            sql.append(" AND create_at >= ?");
            args.add(from);
        }
        if (to != null) {
            sql.append(" AND create_at <= ?");
            args.add(to);
        }
        sql.append(" GROUP BY DATE(create_at), alert_code, trigger_type ORDER BY stat_date DESC, alert_code");
        List<Map<String, Object>> rows = dynamicDao.nativeQueryForMapList(sql.toString(), args.toArray());
        List<Map<String, Object>> stats = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("statDate", pick(row, "stat_date", "statDate"));
                item.put("alertCode", pick(row, "alert_code", "alertCode"));
                item.put("triggerType", pick(row, "trigger_type", "triggerType"));
                item.put("recordCount", pick(row, "record_count", "recordCount"));
                item.put("sentCount", pick(row, "sent_count", "sentCount"));
                item.put("failCount", pick(row, "fail_count", "failCount"));
                item.put("silencedCount", pick(row, "silenced_count", "silencedCount"));
                stats.add(item);
            }
        }
        return stats;
    }

    /**
     * 规则近 7 日发送量（pageQuery withStats）：alertCode → 记录数。
     *
     * @return {@code {alertCode: count}}
     */
    public Map<String, Object> sendStats7d(List<String> alertCodes) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (alertCodes == null || alertCodes.isEmpty()) {
            return result;
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < alertCodes.size(); i++) {
            placeholders.append(i == 0 ? "?" : ",?");
        }
        String sql = "SELECT alert_code, COUNT(*) AS sent_count FROM platform_alert "
                + "WHERE del_status = 0 AND tenant_code = ? AND create_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) "
                + "AND alert_code IN (" + placeholders + ") GROUP BY alert_code";
        List<Object> args = new ArrayList<>();
        args.add(SessionCtxHelper.getCurrentTenantCode());
        args.addAll(alertCodes);
        List<Map<String, Object>> rows = dynamicDao.nativeQueryForMapList(sql.toString(), args.toArray());
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object code = pick(row, "alert_code", "alertCode");
                Object count = pick(row, "sent_count", "sentCount");
                if (code != null) {
                    result.put(String.valueOf(code), count);
                }
            }
        }
        return result;
    }

    // ==================== 内部辅助 ====================

    /** 新增记录审计位（与异常侧同口径：save 不自动填充，需显式赋值）。 */
    private void fillNewAudit(FmsAlert record) {
        Date now = new Date();
        String userId = SessionCtxHelper.getCurrentUserId();
        record.setDelStatus(0);
        record.setCreateAt(now);
        record.setCreator(userId);
        record.setCreatorName(SessionCtxHelper.getCurrentUserName());
        record.setUpdateAt(now);
        record.setUpdater(userId);
        record.setUpdaterName(SessionCtxHelper.getCurrentUserName());
        record.setTenantCode(SessionCtxHelper.getCurrentTenantCode());
    }

    /** 原生行键名兼容：不同驱动可能返回 snake_case 或 camelCase。 */
    private Object pick(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }
}
