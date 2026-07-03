package cn.geelato.web.platform.srv.report.service;

import cn.geelato.orm.MetaFactory;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.platform.srv.report.dto.CustomerReportConfig;
import cn.geelato.web.platform.srv.report.dto.ReportConfigDetailData;
import cn.geelato.web.platform.srv.report.dto.ReportConfigSaveRequest;
import cn.geelato.web.platform.srv.report.dto.ReportTypeConfig;
import cn.geelato.web.platform.srv.report.dto.TemplateMaterial;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReportConfigEditorDatabaseService {
    private static final String DEFAULT_OPERATOR = "report-config";
    private static final String DEFAULT_TENANT = "geelato";

    public ReportConfigDetailData loadDetail(String templateId, String reportTypeId, String customerId) {
        ReportConfigDetailData detail = new ReportConfigDetailData();
        List<ReportTypeConfig> reportTypes = listReportTypes();
        detail.setReportTypes(reportTypes);
        String resolvedReportTypeId = resolveReportTypeId(reportTypeId, reportTypes);
        CustomerReportConfig customerConfig = getCustomerConfig(resolvedReportTypeId, customerId, templateId);
        detail.setCustomerConfig(customerConfig);
        detail.setCurrentTemplate(toCurrentTemplate(customerConfig));
        return detail;
    }

    public ReportConfigDetailData saveDetail(ReportConfigSaveRequest request) {
        if (request == null || request.getTemplate() == null) {
            throw new IllegalArgumentException("template is required");
        }
        CustomerReportConfig customerConfig = mergeCustomerConfig(request.getCustomerConfig(), request.getTemplate());
        customerConfig = saveCustomerConfig(customerConfig);
        ReportConfigDetailData detail = loadDetail(customerConfig.getSourceTemplateId(), customerConfig.getReportTypeId(), customerConfig.getTargetId());
        detail.setCustomerConfig(customerConfig);
        detail.setCurrentTemplate(toCurrentTemplate(customerConfig));
        return detail;
    }

    private List<ReportTypeConfig> listReportTypes() {
        String sql = "SELECT id, report_code, report_name, scope_type, data_provider_code, renderer_type, enabled, tenant_code "
                + "FROM rn_report_type WHERE del_status = 0 AND enabled = 1 AND scope_type = 'CUSTOMER' "
                + "ORDER BY report_name, scope_type";
        try {
            List<Map<String, Object>> rows = MetaFactory.sql(sql).list();
            return rows.stream().map(this::mapReportType).toList();
        } catch (Exception e) {
            log.warn("list report types failed", e);
            return Collections.emptyList();
        }
    }

    private String resolveReportTypeId(String reportTypeId, List<ReportTypeConfig> reportTypes) {
        if (!isBlank(reportTypeId) && reportTypes != null) {
            boolean matched = reportTypes.stream().anyMatch(type -> reportTypeId.equals(type.getId()));
            if (matched) {
                return reportTypeId;
            }
        }
        if (!isBlank(reportTypeId) && (reportTypes == null || reportTypes.isEmpty())) {
            return reportTypeId;
        }
        if (reportTypes == null || reportTypes.isEmpty()) {
            return null;
        }
        return reportTypes.get(0).getId();
    }

    private TemplateMaterial queryDefinitionById(String id) {
        if (isBlank(id)) {
            return null;
        }
        String sql = "SELECT id, report_code, template_name, template_engine, template_content, template_schema, enabled, tenant_code "
                + "FROM rn_report_type WHERE del_status = 0 AND id = ?";
        try {
            Map<String, Object> row = MetaFactory.sql(sql).param(id).one();
            return row == null ? null : mapTemplate(row);
        } catch (Exception e) {
            log.warn("query report type definition failed: {}", id, e);
            return null;
        }
    }

    private TemplateMaterial queryBaseDefinition(String reportTypeId) {
        if (isBlank(reportTypeId)) {
            return null;
        }
        String sql = "SELECT id, report_code, template_name, template_engine, template_content, template_schema, enabled, tenant_code "
                + "FROM rn_report_type WHERE del_status = 0 AND id = ? LIMIT 1";
        try {
            Map<String, Object> row = MetaFactory.sql(sql).param(reportTypeId).one();
            return row == null ? null : mapTemplate(row);
        } catch (Exception e) {
            log.warn("query base report definition failed", e);
            return null;
        }
    }

    private CustomerReportConfig getCustomerConfig(String reportTypeId, String customerId, String sourceTemplateId) {
        if (isBlank(reportTypeId) || isBlank(customerId)) {
            return null;
        }
        String sql = "SELECT id, report_type_id, config_name, scope_type, target_type, target_id, "
                + "template_name, template_engine, template_content, template_schema, source_template_id, llm_prompt_config, "
                + "schedule_type, schedule_expr, timezone, channels, recipient_rule_json, biz_filter_json, enabled, last_dispatch_at, tenant_code "
                + "FROM rn_customer_report_config WHERE del_status = 0 AND report_type_id = ? AND target_id = ? "
                + "ORDER BY update_at DESC LIMIT 1";
        try {
            Map<String, Object> row = MetaFactory.sql(sql).params(reportTypeId, customerId).one();
            if (row == null || row.isEmpty()) {
                return buildDefaultCustomerConfig(reportTypeId, customerId, sourceTemplateId);
            }
            return applyTemplateDefaults(mapCustomerConfig(row), reportTypeId, customerId);
        } catch (Exception e) {
            log.warn("get customer config failed", e);
            return buildDefaultCustomerConfig(reportTypeId, customerId, sourceTemplateId);
        }
    }

    private CustomerReportConfig buildDefaultCustomerConfig(String reportTypeId, String customerId, String sourceTemplateId) {
        TemplateMaterial sourceDefinition = resolveSourceDefinition(reportTypeId, sourceTemplateId);
        CustomerReportConfig config = new CustomerReportConfig();
        config.setReportTypeId(reportTypeId);
        config.setConfigName("客户报告配置");
        config.setScopeType("CUSTOMER");
        config.setTargetType("CUSTOMER");
        config.setTargetId(customerId);
        config.setTemplateName(defaultTemplateName(sourceDefinition, customerId));
        config.setTemplateEngine(sourceDefinition == null ? "HTML" : blankToDefault(sourceDefinition.getTemplateEngine(), "HTML"));
        config.setTemplateContent(sourceDefinition == null ? "" : blankToDefault(sourceDefinition.getTemplateContent(), ""));
        config.setTemplateSchema(sourceDefinition == null ? "{}" : blankToDefault(sourceDefinition.getTemplateSchema(), "{}"));
        config.setSourceTemplateId(sourceDefinition == null ? blankToDefault(sourceTemplateId, "") : sourceDefinition.getId());
        config.setLlmPromptConfig("");
        config.setScheduleType("CRON");
        config.setScheduleExpr("");
        config.setTimezone("Asia/Shanghai");
        config.setChannels("email");
        config.setRecipientRuleJson("{}");
        config.setBizFilterJson("{}");
        config.setEnabled(true);
        config.setTenantCode(DEFAULT_TENANT);
        return config;
    }

    private CustomerReportConfig saveCustomerConfig(CustomerReportConfig config) {
        CustomerReportConfig next = applyTemplateDefaults(config,
                config == null ? null : config.getReportTypeId(),
                config == null ? null : config.getTargetId());
        Timestamp now = new Timestamp(System.currentTimeMillis());
        next.setReportTypeId(blankToDefault(next.getReportTypeId(), ""));
        next.setScopeType(blankToDefault(next.getScopeType(), "CUSTOMER"));
        next.setTargetType(blankToDefault(next.getTargetType(), "CUSTOMER"));
        next.setTargetId(blankToDefault(next.getTargetId(), ""));
        next.setConfigName(blankToDefault(next.getConfigName(), "客户报告配置"));
        next.setTemplateName(blankToDefault(next.getTemplateName(), "客户报告模板"));
        next.setTemplateEngine(blankToDefault(next.getTemplateEngine(), "HTML"));
        next.setTemplateContent(blankToDefault(next.getTemplateContent(), ""));
        next.setTemplateSchema(blankToDefault(next.getTemplateSchema(), "{}"));
        next.setSourceTemplateId(blankToDefault(next.getSourceTemplateId(), ""));
        next.setLlmPromptConfig(blankToDefault(next.getLlmPromptConfig(), ""));
        next.setScheduleType(blankToDefault(next.getScheduleType(), "CRON"));
        next.setTimezone(blankToDefault(next.getTimezone(), "Asia/Shanghai"));
        next.setChannels(blankToDefault(next.getChannels(), "email"));
        next.setRecipientRuleJson(blankToDefault(next.getRecipientRuleJson(), "{}"));
        next.setBizFilterJson(blankToDefault(next.getBizFilterJson(), "{}"));
        next.setTenantCode(blankToDefault(next.getTenantCode(), DEFAULT_TENANT));
        String id = next.getId();
        if (isBlank(id)) {
            id = resolveCustomerConfigId(next.getReportTypeId(), next.getTargetId(), next.getTenantCode());
        }
        id = blankToDefault(id, String.valueOf(UIDGenerator.generate()));
        next.setId(id);

        if (exists("rn_customer_report_config", id)) {
            String sql = "UPDATE rn_customer_report_config SET report_type_id = ?, config_name = ?, scope_type = ?, target_type = ?, "
                    + "target_id = ?, template_name = ?, template_engine = ?, template_content = ?, template_schema = ?, source_template_id = ?, "
                    + "llm_prompt_config = ?, schedule_type = ?, schedule_expr = ?, timezone = ?, channels = ?, recipient_rule_json = ?, "
                    + "biz_filter_json = ?, enabled = ?, tenant_code = ?, update_at = ?, updater = ? WHERE id = ?";
            MetaFactory.sql(sql).params(
                    next.getReportTypeId(), next.getConfigName(), next.getScopeType(), next.getTargetType(), next.getTargetId(),
                    next.getTemplateName(), next.getTemplateEngine(), next.getTemplateContent(), next.getTemplateSchema(),
                    next.getSourceTemplateId(), next.getLlmPromptConfig(), next.getScheduleType(), next.getScheduleExpr(),
                    next.getTimezone(), next.getChannels(), next.getRecipientRuleJson(), next.getBizFilterJson(),
                    next.isEnabled(), next.getTenantCode(), now, DEFAULT_OPERATOR, id
            ).execute();
        } else {
            String sql = "INSERT INTO rn_customer_report_config (id, report_type_id, config_name, scope_type, target_type, target_id, "
                    + "template_name, template_engine, template_content, template_schema, source_template_id, llm_prompt_config, schedule_type, "
                    + "schedule_expr, timezone, channels, recipient_rule_json, biz_filter_json, enabled, last_dispatch_at, tenant_code, del_status, "
                    + "update_at, updater, create_at, creator) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)";
            MetaFactory.sql(sql).params(
                    id, next.getReportTypeId(), next.getConfigName(), next.getScopeType(), next.getTargetType(), next.getTargetId(),
                    next.getTemplateName(), next.getTemplateEngine(), next.getTemplateContent(), next.getTemplateSchema(),
                    next.getSourceTemplateId(), next.getLlmPromptConfig(), next.getScheduleType(), next.getScheduleExpr(),
                    next.getTimezone(), next.getChannels(), next.getRecipientRuleJson(), next.getBizFilterJson(),
                    next.isEnabled(), next.getLastDispatchAt(), next.getTenantCode(),
                    now, DEFAULT_OPERATOR, now, DEFAULT_OPERATOR
            ).execute();
        }
        return next;
    }

    private String resolveCustomerConfigId(String reportTypeId, String customerId, String tenantCode) {
        if (isBlank(reportTypeId) || isBlank(customerId)) {
            return null;
        }
        String sql = "SELECT id FROM rn_customer_report_config "
                + "WHERE del_status = 0 AND report_type_id = ? AND target_id = ? AND tenant_code = ? "
                + "ORDER BY update_at DESC LIMIT 1";
        try {
            Map<String, Object> row = MetaFactory.sql(sql)
                    .params(reportTypeId, customerId, blankToDefault(tenantCode, DEFAULT_TENANT))
                    .one();
            return row == null ? null : asString(row, "id");
        } catch (Exception e) {
            log.warn("resolve customer config id failed", e);
            return null;
        }
    }

    private CustomerReportConfig mergeCustomerConfig(CustomerReportConfig customerConfig, TemplateMaterial template) {
        if (template == null) {
            throw new IllegalArgumentException("template is required");
        }
        CustomerReportConfig next = customerConfig == null ? new CustomerReportConfig() : customerConfig;
        next.setReportTypeId(blankToDefault(template.getReportTypeId(), next.getReportTypeId()));
        next.setTargetId(blankToDefault(next.getTargetId(), template.getCustomerId()));
        next.setScopeType(blankToDefault(next.getScopeType(), "CUSTOMER"));
        next.setTargetType(blankToDefault(next.getTargetType(), "CUSTOMER"));
        next.setConfigName(blankToDefault(next.getConfigName(), "客户报告配置"));
        next.setTemplateName(blankToDefault(template.getTemplateName(), next.getTemplateName()));
        next.setTemplateEngine(blankToDefault(template.getTemplateEngine(), next.getTemplateEngine()));
        next.setTemplateContent(blankToDefault(template.getTemplateContent(), next.getTemplateContent()));
        next.setTemplateSchema(blankToDefault(template.getTemplateSchema(), next.getTemplateSchema()));
        next.setSourceTemplateId(blankToDefault(template.getSourceTemplateId(), next.getSourceTemplateId()));
        next.setLlmPromptConfig(blankToDefault(template.getLlmPromptConfig(), next.getLlmPromptConfig()));
        next.setTenantCode(blankToDefault(next.getTenantCode(), blankToDefault(template.getTenantCode(), DEFAULT_TENANT)));
        return next;
    }

    private CustomerReportConfig applyTemplateDefaults(CustomerReportConfig config, String reportTypeId, String customerId) {
        if (config == null) {
            return buildDefaultCustomerConfig(reportTypeId, customerId, null);
        }
        TemplateMaterial sourceDefinition = resolveSourceDefinition(
                blankToDefault(config.getReportTypeId(), reportTypeId),
                config.getSourceTemplateId()
        );
        config.setReportTypeId(blankToDefault(config.getReportTypeId(), reportTypeId));
        config.setTargetId(blankToDefault(config.getTargetId(), customerId));
        config.setTemplateName(blankToDefault(config.getTemplateName(), defaultTemplateName(sourceDefinition, config.getTargetId())));
        config.setTemplateEngine(blankToDefault(config.getTemplateEngine(), sourceDefinition == null ? "HTML" : sourceDefinition.getTemplateEngine()));
        config.setTemplateContent(blankToDefault(config.getTemplateContent(), sourceDefinition == null ? "" : sourceDefinition.getTemplateContent()));
        config.setTemplateSchema(blankToDefault(config.getTemplateSchema(), sourceDefinition == null ? "{}" : sourceDefinition.getTemplateSchema()));
        config.setSourceTemplateId(blankToDefault(config.getSourceTemplateId(), sourceDefinition == null ? "" : sourceDefinition.getId()));
        config.setLlmPromptConfig(blankToDefault(config.getLlmPromptConfig(), ""));
        config.setTenantCode(blankToDefault(config.getTenantCode(), DEFAULT_TENANT));
        return config;
    }

    private TemplateMaterial resolveSourceDefinition(String reportTypeId, String sourceTemplateId) {
        TemplateMaterial sourceDefinition = queryDefinitionById(sourceTemplateId);
        if (sourceDefinition != null) {
            return sourceDefinition;
        }
        return queryBaseDefinition(reportTypeId);
    }

    private TemplateMaterial toCurrentTemplate(CustomerReportConfig customerConfig) {
        if (customerConfig == null) {
            return null;
        }
        TemplateMaterial template = new TemplateMaterial();
        template.setId(customerConfig.getId());
        template.setReportTypeId(customerConfig.getReportTypeId());
        template.setTemplateCode(buildTemplateCode(customerConfig.getReportTypeId(), customerConfig.getTargetId()));
        template.setTemplateName(customerConfig.getTemplateName());
        template.setTemplateKind("CUSTOMER");
        template.setTemplateEngine(customerConfig.getTemplateEngine());
        template.setTemplateContent(customerConfig.getTemplateContent());
        template.setTemplateSchema(customerConfig.getTemplateSchema());
        template.setSourceTemplateId(customerConfig.getSourceTemplateId());
        template.setCustomerId(customerConfig.getTargetId());
        template.setStatus(customerConfig.isEnabled() ? "ACTIVE" : "DISABLED");
        template.setLlmPromptConfig(customerConfig.getLlmPromptConfig());
        template.setTenantCode(customerConfig.getTenantCode());
        return template;
    }

    private String defaultTemplateName(TemplateMaterial sourceTemplate, String customerId) {
        if (sourceTemplate != null && !isBlank(sourceTemplate.getTemplateName())) {
            return sourceTemplate.getTemplateName();
        }
        return "客户报告模板-" + blankToDefault(customerId, "customer");
    }

    private String buildTemplateCode(String reportTypeId, String customerId) {
        return "customer_template_" + blankToDefault(reportTypeId, "report")
                + "_" + blankToDefault(customerId, "customer");
    }

    private boolean exists(String tableName, String id) {
        if (isBlank(id)) {
            return false;
        }
        Long count = MetaFactory.sql("SELECT COUNT(1) FROM " + tableName + " WHERE id = ? AND del_status = 0")
                .param(id)
                .queryForObject(Long.class);
        return count != null && count > 0;
    }

    private ReportTypeConfig mapReportType(Map<String, Object> row) {
        ReportTypeConfig config = new ReportTypeConfig();
        config.setId(asString(row, "id"));
        config.setReportCode(asString(row, "report_code"));
        config.setReportName(asString(row, "report_name"));
        config.setScopeType(asString(row, "scope_type"));
        config.setDataProviderCode(asString(row, "data_provider_code"));
        config.setRendererType(asString(row, "renderer_type"));
        config.setEnabled(asBoolean(row, "enabled"));
        config.setTenantCode(asString(row, "tenant_code"));
        return config;
    }

    private TemplateMaterial mapTemplate(Map<String, Object> row) {
        TemplateMaterial material = new TemplateMaterial();
        material.setId(asString(row, "id"));
        material.setReportTypeId(asString(row, "id"));
        material.setTemplateCode(asString(row, "report_code"));
        material.setTemplateName(asString(row, "template_name"));
        material.setTemplateKind("BASE");
        material.setTemplateEngine(asString(row, "template_engine"));
        material.setTemplateContent(asString(row, "template_content"));
        material.setTemplateSchema(asString(row, "template_schema"));
        material.setStatus(asBoolean(row, "enabled") ? "ACTIVE" : "DISABLED");
        material.setTenantCode(asString(row, "tenant_code"));
        return material;
    }

    private CustomerReportConfig mapCustomerConfig(Map<String, Object> row) {
        CustomerReportConfig config = new CustomerReportConfig();
        config.setId(asString(row, "id"));
        config.setReportTypeId(asString(row, "report_type_id"));
        config.setConfigName(asString(row, "config_name"));
        config.setScopeType(asString(row, "scope_type"));
        config.setTargetType(asString(row, "target_type"));
        config.setTargetId(asString(row, "target_id"));
        config.setTemplateName(asString(row, "template_name"));
        config.setTemplateEngine(asString(row, "template_engine"));
        config.setTemplateContent(asString(row, "template_content"));
        config.setTemplateSchema(asString(row, "template_schema"));
        config.setSourceTemplateId(asString(row, "source_template_id"));
        config.setLlmPromptConfig(asString(row, "llm_prompt_config"));
        config.setScheduleType(asString(row, "schedule_type"));
        config.setScheduleExpr(asString(row, "schedule_expr"));
        config.setTimezone(asString(row, "timezone"));
        config.setChannels(asString(row, "channels"));
        config.setRecipientRuleJson(asString(row, "recipient_rule_json"));
        config.setBizFilterJson(asString(row, "biz_filter_json"));
        config.setEnabled(asBoolean(row, "enabled"));
        config.setLastDispatchAt(asTimestamp(row.get("last_dispatch_at")));
        config.setTenantCode(asString(row, "tenant_code"));
        return config;
    }

    private String asString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean asBoolean(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String stringValue) {
            return "1".equals(stringValue) || "true".equalsIgnoreCase(stringValue) || "y".equalsIgnoreCase(stringValue);
        }
        return false;
    }

    private Timestamp asTimestamp(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp;
        }
        if (value instanceof Date date) {
            return new Timestamp(date.getTime());
        }
        return null;
    }

    private String blankToDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
