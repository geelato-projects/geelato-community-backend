package cn.geelato.archive.engine.controller;

import cn.geelato.archive.entity.ArchivePolicy;
import cn.geelato.archive.entity.ArchiveRun;
import cn.geelato.archive.exception.ArchiveException;
import cn.geelato.archive.service.ArchivePolicyService;
import cn.geelato.archive.service.ArchiveRunService;
import cn.geelato.archive.engine.channel.MySqlArchiveChannel;
import cn.geelato.archive.engine.trigger.ArchiveTrigger;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Map;

/**
 * 归档运行接口：运行历史、手动触发/中止、归档数据在线查询、回迁。
 * <p>与策略管理接口一样自包含在本归档模块内（宿主引入即生效，登录鉴权走平台拦截器）。</p>
 */
@ApiRestController("/archive/run")
@Slf4j
public class ArchiveRunController {

    private final ArchiveRunService runService;
    private final ArchivePolicyService policyService;
    private final ArchiveTrigger trigger;
    private final MySqlArchiveChannel channel;

    @Autowired
    public ArchiveRunController(ArchiveRunService runService,
                                ArchivePolicyService policyService,
                                ArchiveTrigger trigger,
                                MySqlArchiveChannel channel) {
        this.runService = runService;
        this.policyService = policyService;
        this.trigger = trigger;
        this.channel = channel;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery(@RequestBody(required = false) Map<String, Object> body) {
        try {
            PageQueryRequest request = new PageQueryRequest();
            FilterGroup filterGroup = new FilterGroup();
            if (body != null) {
                Object pageNum = body.get("pageNum");
                Object pageSize = body.get("pageSize");
                Object orderBy = body.get("orderBy");
                if (pageNum instanceof Number n) {
                    request.setPageNum(n.intValue());
                }
                if (pageSize instanceof Number n) {
                    request.setPageSize(n.intValue());
                }
                if (orderBy instanceof String s && !s.isBlank()) {
                    request.setOrderBy(s);
                }
                Object policyId = body.get("policyId");
                if (policyId instanceof String s && !s.isBlank()) {
                    filterGroup.addFilter("policyId", s);
                }
                Object status = body.get("status");
                if (status instanceof String s && !s.isBlank()) {
                    filterGroup.addFilter("status", s);
                }
            }
            if (request.getPageNum() <= 0) {
                request.setPageNum(1);
            }
            if (request.getPageSize() <= 0) {
                request.setPageSize(20);
            }
            if (request.getOrderBy() == null || request.getOrderBy().isBlank()) {
                request.setOrderBy("begin_at desc");
            }
            return runService.pageQuery(filterGroup, request);
        } catch (ArchiveException e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<ArchiveRun> get(@PathVariable String id) {
        try {
            return ApiResult.success(runService.get(id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** 手动触发单策略（同步执行；大批量耗时长，前端宜配超时或改异步由运行历史观察） */
    @RequestMapping(value = "/trigger/{policyId}", method = RequestMethod.POST)
    public ApiResult<ArchiveRun> trigger(@PathVariable String policyId) {
        try {
            return ApiResult.success(trigger.runPolicyNow(policyId));
        } catch (ArchiveException e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** 手动一次性执行全部启用策略（跑完即静默，无常驻线程；单策略失败不影响其他） */
    @RequestMapping(value = "/triggerAll", method = RequestMethod.POST)
    public ApiResult<Map<String, Object>> triggerAll() {
        try {
            return ApiResult.success(trigger.runAllNow());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** 优雅中止：完成当前批后停止，run 记为 canceled */
    @RequestMapping(value = "/cancel/{policyId}", method = RequestMethod.POST)
    public ApiResult<String> cancel(@PathVariable String policyId) {
        try {
            boolean accepted = trigger.cancel(policyId);
            return ApiResult.success(accepted ? "中止请求已受理，当前批完成后停止" : "该策略当前没有运行中的任务");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** 归档数据在线只读查询（分页，可选 id 等值过滤） */
    @RequestMapping(value = "/archivedData", method = RequestMethod.POST)
    public ApiResult<Map<String, Object>> archivedData(@RequestBody Map<String, Object> body) {
        try {
            String policyId = str(body.get("policyId"));
            if (policyId == null) {
                return ApiResult.fail(ArchiveException.VALIDATION_ERROR, "policyId 不能为空");
            }
            ArchivePolicy policy = policyService.get(policyId);
            if (policy == null) {
                return ApiResult.fail(ArchiveException.VALIDATION_ERROR, "归档策略不存在：" + policyId);
            }
            String resolvedTarget = policyService.resolveTargetTableName(policy);
            int pageNum = intVal(body.get("pageNum"), 1);
            int pageSize = intVal(body.get("pageSize"), 20);
            Map<String, Object> filters = new java.util.HashMap<>();
            if (body.get("id") instanceof String s && !s.isBlank()) {
                filters.put("id", s);
            }
            Map<String, Object> result = channel.queryArchived(policy, resolvedTarget, filters, pageNum, pageSize);
            result.put("targetTable", resolvedTarget);
            return ApiResult.success(result);
        } catch (ArchiveException e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** 回迁：按 id 列表从归档库搬回源表（反向两阶段对账） */
    @RequestMapping(value = "/restore", method = RequestMethod.POST)
    public ApiResult<ArchiveRun> restore(@RequestBody Map<String, Object> body) {
        try {
            String policyId = str(body.get("policyId"));
            Object rawIds = body == null ? null : body.get("ids");
            if (!(rawIds instanceof List<?> list) || list.isEmpty()) {
                return ApiResult.fail(ArchiveException.VALIDATION_ERROR, "ids（主键列表）不能为空");
            }
            List<String> ids = list.stream().map(String::valueOf).toList();
            return ApiResult.success(trigger.restore(policyId, ids));
        } catch (ArchiveException e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    private String str(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    private int intVal(Object value, int defaultValue) {
        return value instanceof Number n ? n.intValue() : defaultValue;
    }
}
