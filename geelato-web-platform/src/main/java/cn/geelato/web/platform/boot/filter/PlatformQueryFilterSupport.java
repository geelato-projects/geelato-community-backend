package cn.geelato.web.platform.boot.filter;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.security.Permission;
import cn.geelato.security.PermissionRuleUtils;
import org.springframework.util.StringUtils;

import java.util.Objects;

final class PlatformQueryFilterSupport {

    private static final String TENANT_CODE = "tenantCode";

    void applyDefaultFilters(QueryCommand command) {
        if (command == null) {
            return;
        }
        applyTenantFilter(command);
        applyDataPermissionFilter(command);
    }

    void applyTenantFilter(QueryCommand command) {
        if (!StringUtils.hasText(SessionCtx.getCurrentTenantCode())) {
            return;
        }
        FilterGroup where = ensureWhere(command);
        if (containsField(where, TENANT_CODE)) {
            return;
        }
        where.addFilter(TENANT_CODE, SessionCtx.getCurrentTenantCode());
    }

    void applyDataPermissionFilter(QueryCommand command) {
        if (!StringUtils.hasText(command.getEntityName())) {
            return;
        }
        String resolved = resolveOriginalWhere(command.getEntityName());
        if (!StringUtils.hasText(resolved)) {
            return;
        }
        if (!StringUtils.hasText(command.getOriginalWhere())) {
            command.setOriginalWhere(resolved);
            return;
        }
        command.setOriginalWhere("(" + command.getOriginalWhere() + ") and (" + resolved + ")");
    }

    String resolveOriginalWhere(String entityName) {
        Permission dp = SessionCtx.getCurrentUser().getDataPermissionByEntity(entityName);
        if (dp == null) {
            return String.format("creator='%s'", SessionCtx.getCurrentUser().getUserId());
        }
        String rule = PermissionRuleUtils.replaceRuleVariable(dp, SessionCtx.getCurrentUser());
        PermissionRuleUtils.validateResolvedRule(dp, rule);
        return rule;
    }

    private FilterGroup ensureWhere(QueryCommand command) {
        if (command.getWhere() == null) {
            command.setWhere(new FilterGroup());
        }
        return command.getWhere();
    }

    private boolean containsField(FilterGroup filterGroup, String fieldName) {
        if (filterGroup == null || !StringUtils.hasText(fieldName)) {
            return false;
        }
        return filterGroup.getFilters().stream()
                .map(FilterGroup.Filter::getField)
                .filter(Objects::nonNull)
                .anyMatch(fieldName::equals);
    }
}
