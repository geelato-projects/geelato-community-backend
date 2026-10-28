package cn.geelato.alert.controller;

import cn.geelato.alert.entity.FmsAlert;
import cn.geelato.alert.entity.FmsAlertTemplate;
import cn.geelato.alert.service.AlertRecordService;
import cn.geelato.alert.service.AlertTemplateService;
import cn.geelato.alert.support.ContractCodes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息模板管理 Controller（告警管理员）。
 *
 * <p>由 ApiPrefixAutoConfiguration 自动加 /api 前缀，实际路径 = {@code /api/alert/template}。
 *
 * <p>端点：pageQuery / {code} / save / {code}/delete / test-render / context-schema / context-from-record/{recordId}。
 *
 * <p>关联文档：bea/业务告警中心-接口设计-2026-09-22.md §3
 */
@Slf4j
@ApiRuntimeRestController("/alert/template")
public class AlertTemplateController {

    @Autowired
    private AlertTemplateService templateService;

    @Autowired
    private AlertRecordService recordService;

    /** 模板分页（编码/渠道变体/上下文形态/启停筛选）。 */
    @PostMapping("/pageQuery")
    public ApiResult<Map<String, Object>> pageQuery(
            @RequestBody(required = false) AlertTemplateService.TemplateQueryParams params) {
        AlertTemplateService.TemplateQueryParams p = params == null
                ? new AlertTemplateService.TemplateQueryParams() : params;
        return ApiResult.success(templateService.pageQuery(p));
    }

    /** 模板详情（该 code 全部渠道变体 + 引用它的规则清单，限定当前应用）。 */
    @GetMapping("/{code}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String code, @RequestParam String appId) {
        List<FmsAlertTemplate> variants = templateService.listVariants(code, appId);
        if (variants.isEmpty()) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, "模板不存在: " + code);
        }
        return ApiResult.success(templateService.detailByCode(code, appId));
    }

    /** 新增/编辑变体（UK: code+channel；version 递增；语法 parseOnly 预检留后续）。 */
    @PostMapping("/save")
    public ApiResult<Map<String, Object>> save(@RequestBody AlertTemplateService.TemplateSaveRequest req) {
        try {
            FmsAlertTemplate saved = templateService.save(req);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", saved.getId());
            data.put("templateCode", saved.getTemplateCode());
            data.put("channel", saved.getChannel());
            data.put("version", saved.getVersion());
            return ApiResult.success(data);
        } catch (IllegalArgumentException e) {
            return ContractCodes.fail(ContractCodes.PARAM_INVALID, e.getMessage());
        }
    }

    /** 删除模板（按 code 逻辑删除全部渠道变体；被规则引用则拒绝）。 */
    @PostMapping("/{code}/delete")
    public ApiResult<Map<String, Object>> delete(@PathVariable String code, @RequestParam String appId) {
        if (templateService.listVariants(code, appId).isEmpty()) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, "模板不存在: " + code);
        }
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("deleted", templateService.deleteByCode(code, appId));
            return ApiResult.success(data);
        } catch (IllegalStateException e) {
            return ContractCodes.fail(ContractCodes.NOT_PERMITTED, e.getMessage());
        }
    }

    /** 试渲染（示例上下文 → 各渠道变体输出预览；不落库不投递）。 */
    @PostMapping("/test-render")
    public ApiResult<Map<String, Object>> testRender(@RequestBody AlertTemplateService.TestRenderRequest req) {
        try {
            if (req == null || req.templateCode == null || req.templateCode.isBlank()) {
                return ContractCodes.fail(ContractCodes.PARAM_INVALID, "templateCode 必填");
            }
            return ApiResult.success(templateService.testRender(req.templateCode.trim(), req.context, req.appId));
        } catch (IllegalArgumentException e) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, e.getMessage());
        }
    }

    /** 上下文字典（配置页速查面板数据源；SINGLE 形态按 sourceType 区分事件源上下文）。 */
    @GetMapping("/context-schema")
    public ApiResult<Map<String, Object>> contextSchema(
            @RequestParam(required = false, defaultValue = "single") String type,
            @RequestParam(required = false, defaultValue = "EXCEPTION") String sourceType) {
        try {
            return ApiResult.success(templateService.contextSchema(type, sourceType));
        } catch (IllegalArgumentException e) {
            return ContractCodes.fail(ContractCodes.PARAM_INVALID, e.getMessage());
        }
    }

    /** 用历史告警记录构造同构示例上下文（真实数据预演）。 */
    @GetMapping("/context-from-record/{recordId}")
    public ApiResult<Map<String, Object>> contextFromRecord(@PathVariable String recordId) {
        FmsAlert record = recordService.getById(recordId);
        if (record == null) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, "告警记录不存在: " + recordId);
        }
        Map<String, Object> detail = recordService.detail(record);
        List<Map<String, Object>> exceptions = asMapList(detail.get("exceptions"));
        Map<String, Object> context = new LinkedHashMap<>();
        if (exceptions.size() > 1) {
            // group 形态
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("alertCode", record.getAlertCode());
            group.put("groupKey", record.getGroupKey());
            group.put("items", exceptions);
            group.put("firstEventAt", record.getFirstEventAt());
            group.put("lastEventAt", record.getLastEventAt());
            group.put("stats", detail.get("stats"));
            context.put("group", group);
        } else {
            // single 形态
            context.put("exception", exceptions.isEmpty() ? Map.of() : exceptions.get(0));
            context.put("eventType", record.getEventType());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordId", recordId);
        result.put("contextType", exceptions.size() > 1 ? "GROUP" : "SINGLE");
        result.put("context", context);
        return ApiResult.success(result);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            return out;
        }
        return List.of();
    }
}
