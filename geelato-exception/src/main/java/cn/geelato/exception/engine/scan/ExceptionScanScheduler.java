package cn.geelato.exception.engine.scan;

import cn.geelato.core.orm.Dao;
import cn.geelato.exception.engine.support.EngineContextExecutor;
import cn.geelato.exception.entity.FmsExceptionRule;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 扫描调度器（自管理 ScheduledExecutorService，不依赖宿主 @EnableScheduling）：
 * tick 扫 enable_status=1 且 trigger_type IN (SCAN,BOTH) 的规则，cron 到期提交 ScanRunner。
 * 到期基线 = 该规则最近一次 SCAN 日志 start_at（missed fire 只补一次）。
 */
@Slf4j
public class ExceptionScanScheduler {

    private final Dao dynamicDao;
    private final ScanRunner scanRunner;
    private final int tickSeconds;

    private ScheduledExecutorService ticker;

    public ExceptionScanScheduler(@Qualifier("dynamicDao") Dao dynamicDao,
                                  ScanRunner scanRunner,
                                  int tickSeconds) {
        this.dynamicDao = dynamicDao;
        this.scanRunner = scanRunner;
        this.tickSeconds = Math.max(5, tickSeconds);
    }

    @PostConstruct
    public void start() {
        ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "exception-scan-scheduler");
            t.setDaemon(true);
            return t;
        });
        ticker.scheduleWithFixedDelay(this::safeTick, tickSeconds, tickSeconds, TimeUnit.SECONDS);
        log.info("异常扫描调度器已启动 tick={}s", tickSeconds);
    }

    @PreDestroy
    public void stop() {
        if (ticker != null) {
            ticker.shutdownNow();
        }
    }

    private void safeTick() {
        try {
            List<Map<String, Object>> rows = EngineContextExecutor.withSystemContext(() ->
                    MetaFactory.query(FmsExceptionRule.class)
                            .where(Filter.eq("enableStatus", 1),
                                    Filter.in("triggerType", List.of("SCAN", "BOTH")),
                                    Filter.eq("delStatus", 0))
                            .list());
            if (rows == null || rows.isEmpty()) {
                return;
            }
            Map<String, LocalDateTime> lastRunMap = loadLastRunAt(rows);
            LocalDateTime now = LocalDateTime.now();
            for (Map<String, Object> row : rows) {
                FmsExceptionRule rule = toRule(row);
                if (rule.getId() == null) {
                    continue;
                }
                if (RuleCronCalculator.isDue(rule, lastRunMap.get(rule.getRuleCode()), now)) {
                    scanRunner.submitAsync(rule);
                }
            }
        } catch (Exception e) {
            log.warn("扫描调度 tick 异常", e);
        }
    }

    private Map<String, LocalDateTime> loadLastRunAt(List<Map<String, Object>> ruleRows) {
        Map<String, LocalDateTime> map = new HashMap<>();
        try {
            List<Map<String, Object>> rows = dynamicDao.nativeQueryForMapList(
                    "SELECT rule_code, MAX(start_at) AS last_at FROM platform_exception_scan_log" +
                            " WHERE trigger_type = 'SCAN' AND del_status = 0 GROUP BY rule_code", new Object[]{});
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    LocalDateTime t = RuleCronCalculator.toLocalDateTime(row.get("last_at"));
                    if (t != null && row.get("rule_code") != null) {
                        map.put(String.valueOf(row.get("rule_code")), t);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("加载规则最近扫描时刻失败: {}", e.getMessage());
        }
        return map;
    }

    private static FmsExceptionRule toRule(Map<String, Object> row) {
        FmsExceptionRule rule = new FmsExceptionRule();
        rule.setId(str(row.get("id")));
        rule.setRuleCode(str(row.get("ruleCode")));
        rule.setRuleName(str(row.get("ruleName")));
        rule.setDomainCode(str(row.get("domainCode")));
        rule.setLevel(str(row.get("level")));
        rule.setRuleKind(str(row.get("ruleKind")));
        rule.setTriggerType(str(row.get("triggerType")));
        rule.setCronExpression(str(row.get("cronExpression")));
        rule.setListenEntities(str(row.get("listenEntities")));
        rule.setPipelineJson(str(row.get("pipelineJson")));
        rule.setThresholdJson(str(row.get("thresholdJson")));
        rule.setOwnerSelector(str(row.get("ownerSelector")));
        rule.setResolvePolicy(str(row.get("resolvePolicy")));
        rule.setStormJson(str(row.get("stormJson")));
        rule.setBizType(str(row.get("bizType")));
        rule.setFlowTemplate(str(row.get("flowTemplate")));
        rule.setBizUrlTemplate(str(row.get("bizUrlTemplate")));
        rule.setEngineVersion(str(row.get("engineVersion")));
        rule.setTenantCode(str(row.get("tenantCode")));
        rule.setAppId(str(row.get("appId")));
        rule.setVersion(row.get("version") instanceof Number n ? n.intValue() : 1);
        return rule;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
