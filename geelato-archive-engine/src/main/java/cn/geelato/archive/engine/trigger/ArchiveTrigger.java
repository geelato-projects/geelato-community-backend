package cn.geelato.archive.engine.trigger;

import cn.geelato.archive.entity.ArchivePolicy;
import cn.geelato.archive.entity.ArchiveRun;
import cn.geelato.archive.service.ArchivePolicyService;
import cn.geelato.archive.engine.core.ArchiveEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 归档门面：调度入口与手动操作（触发/中止/回迁）统一走这里。
 * <p>runDuePolicies 串行执行所有启用策略（按 seq_no），单策略失败不影响后续策略。</p>
 */
@Component
@Slf4j
public class ArchiveTrigger {

    private final ArchiveEngine engine;
    private final ArchivePolicyService policyService;

    @Autowired
    public ArchiveTrigger(ArchiveEngine engine, ArchivePolicyService policyService) {
        this.engine = engine;
        this.policyService = policyService;
    }

    /** 每日定时入口：串行执行所有启用策略 */
    public void runDuePolicies() {
        List<ArchivePolicy> policies = policyService.queryEnabledPolicies();
        if (policies == null || policies.isEmpty()) {
            log.debug("无启用中的归档策略，跳过本轮调度");
            return;
        }
        log.info("归档调度开始：共 {} 条启用策略", policies.size());
        int success = 0;
        int failed = 0;
        for (ArchivePolicy policy : policies) {
            try {
                engine.runPolicy(policy.getId());
                success++;
            } catch (Exception e) {
                // 单策略失败（已记录 run 并更新 lastRun）不影响其他策略
                failed++;
                log.error("归档策略执行失败：policyId={}, table={}", policy.getId(), policy.getTableName(), e);
            }
        }
        log.info("归档调度结束：成功 {} 条，失败 {} 条", success, failed);
    }

    /**
     * 手动一次性执行全部启用策略（"把引擎 run 起来"：跑完即静默，无常驻线程）。
     * 单策略失败不影响其他；返回汇总。
     */
    public Map<String, Object> runAllNow() {
        List<ArchivePolicy> policies = policyService.queryEnabledPolicies();
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        if (policies == null || policies.isEmpty()) {
            summary.put("total", 0);
            summary.put("success", 0);
            summary.put("failed", 0);
            summary.put("message", "无启用中的归档策略");
            return summary;
        }
        log.info("手动全量归档开始：共 {} 条启用策略", policies.size());
        int success = 0;
        int failed = 0;
        java.util.List<String> failedPolicyIds = new java.util.ArrayList<>();
        for (ArchivePolicy policy : policies) {
            try {
                engine.runPolicy(policy.getId());
                success++;
            } catch (Exception e) {
                failed++;
                failedPolicyIds.add(policy.getId());
                log.error("归档策略执行失败：policyId={}, table={}", policy.getId(), policy.getTableName(), e);
            }
        }
        summary.put("total", policies.size());
        summary.put("success", success);
        summary.put("failed", failed);
        summary.put("failedPolicyIds", failedPolicyIds);
        log.info("手动全量归档结束：成功 {} 条，失败 {} 条", success, failed);
        return summary;
    }

    /** 手动触发单策略（同步执行，异常传播给调用方） */
    public ArchiveRun runPolicyNow(String policyId) {
        return engine.runPolicy(policyId);
    }

    /** 优雅中止：完成当前批后停止 */
    public boolean cancel(String policyId) {
        return engine.requestCancel(policyId);
    }

    /** 回迁：按 id 列表从归档库搬回源表 */
    public ArchiveRun restore(String policyId, List<String> ids) {
        return engine.restorePolicy(policyId, ids);
    }
}
