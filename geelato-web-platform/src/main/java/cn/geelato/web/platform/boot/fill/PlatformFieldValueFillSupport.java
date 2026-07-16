package cn.geelato.web.platform.boot.fill;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.spi.EntitySaveFieldValueFillContext;
import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.spi.MqlSaveFieldValueFillContext;
import cn.geelato.orm.spi.FluentSaveFieldValueFillContext;
import cn.geelato.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

final class PlatformFieldValueFillSupport {

    private static final String FN_UPDATE_AT = "updateAt";
    private static final String FN_UPDATER = "updater";
    private static final String FN_UPDATER_NAME = "updaterName";
    private static final String FN_CREATE_AT = "createAt";
    private static final String FN_CREATOR = "creator";
    private static final String FN_CREATOR_NAME = "creatorName";
    private static final String FN_TENANT_CODE = ColumnDefault.TENANT_CODE_FIELD;
    private static final String FN_BU_ID = "buId";
    private static final String FN_DEPT_ID = "deptId";
    private static final String FN_DELETE_AT = ColumnDefault.DELETE_AT_FIELD;

    void applyMqlDefaults(MqlSaveFieldValueFillContext context) {
        if (context == null || context.getCommandType() == null) {
            return;
        }
        if (context.getCommandType() == CommandType.Insert) {
            fillMqlInsertDefaults(context.getDefaultEntityMap(), context.getTargetValueMap());
        } else if (context.getCommandType() == CommandType.Update) {
            fillUpdateDefaults(context.getDefaultEntityMap(), context.getTargetValueMap());
        }
    }

    void applyFluentDefaults(FluentSaveFieldValueFillContext context) {
        if (context == null || context.getCommandType() == null) {
            return;
        }
        if (context.getCommandType() == CommandType.Insert) {
            fillFluentInsertDefaults(context.getDefaultEntityMap(), context.getTargetValueMap());
        } else if (context.getCommandType() == CommandType.Update) {
            fillUpdateDefaults(context.getDefaultEntityMap(), context.getTargetValueMap());
        }
    }

    void applyEntityDefaults(EntitySaveFieldValueFillContext context) {
        if (context == null || context.getCommandType() == null) {
            return;
        }
        if (context.getCommandType() == CommandType.Insert) {
            fillEntityInsertDefaults(context.getTargetValueMap());
        } else if (context.getCommandType() == CommandType.Update) {
            fillUpdateDefaults(context.getTargetValueMap(), context.getTargetValueMap());
        }
    }

    private void fillMqlInsertDefaults(Map<String, Object> defaultEntityMap, Map<String, Object> values) {
        String now = nowDateTime();
        if (defaultEntityMap.containsKey(FN_CREATE_AT)) {
            values.put(FN_CREATE_AT, now);
        }
        if (defaultEntityMap.containsKey(FN_CREATOR)) {
            values.put(FN_CREATOR, SessionCtx.getUserId());
        }
        if (defaultEntityMap.containsKey(FN_CREATOR_NAME)) {
            values.put(FN_CREATOR_NAME, SessionCtx.getUserName());
        }
        if (defaultEntityMap.containsKey(FN_TENANT_CODE)) {
            values.put(FN_TENANT_CODE, SessionCtx.getCurrentTenantCode());
        }
        if (defaultEntityMap.containsKey(FN_BU_ID)) {
            values.put(FN_BU_ID, SessionCtx.getCurrentUser().getBuId());
        }
        if (defaultEntityMap.containsKey(FN_DEPT_ID)) {
            values.put(FN_DEPT_ID, SessionCtx.getCurrentUser().getOrgId());
        }
        if (defaultEntityMap.containsKey(FN_UPDATE_AT)) {
            values.put(FN_UPDATE_AT, now);
        }
        if (defaultEntityMap.containsKey(FN_UPDATER)) {
            values.put(FN_UPDATER, SessionCtx.getUserId());
        }
        if (defaultEntityMap.containsKey(FN_UPDATER_NAME)) {
            values.put(FN_UPDATER_NAME, SessionCtx.getUserName());
        }
        if (defaultEntityMap.containsKey(FN_DELETE_AT)) {
            values.put(FN_DELETE_AT, DateUtils.DEFAULT_DELETE_AT);
        }
    }

    private void fillFluentInsertDefaults(Map<String, Object> defaultEntityMap, Map<String, Object> values) {
        String now = nowDateTime();
        if (defaultEntityMap.containsKey(FN_CREATE_AT)) {
            values.put(FN_CREATE_AT, now);
        }
        if (defaultEntityMap.containsKey(FN_CREATOR)) {
            values.put(FN_CREATOR, SessionCtx.getUserId());
        }
        if (defaultEntityMap.containsKey(FN_CREATOR_NAME)) {
            values.put(FN_CREATOR_NAME, SessionCtx.getUserName());
        }
        if (defaultEntityMap.containsKey(FN_TENANT_CODE)) {
            values.put(FN_TENANT_CODE, SessionCtx.getCurrentTenantCode());
        }
        if (defaultEntityMap.containsKey(FN_BU_ID) && SessionCtx.getCurrentUser() != null) {
            values.put(FN_BU_ID, SessionCtx.getCurrentUser().getBuId());
        }
        if (defaultEntityMap.containsKey(FN_DEPT_ID) && SessionCtx.getCurrentUser() != null) {
            values.put(FN_DEPT_ID, SessionCtx.getCurrentUser().getOrgId());
        }
        if (defaultEntityMap.containsKey(FN_UPDATE_AT)) {
            values.put(FN_UPDATE_AT, now);
        }
        if (defaultEntityMap.containsKey(FN_UPDATER)) {
            values.put(FN_UPDATER, SessionCtx.getUserId());
        }
        if (defaultEntityMap.containsKey(FN_UPDATER_NAME)) {
            values.put(FN_UPDATER_NAME, SessionCtx.getUserName());
        }
        if (defaultEntityMap.containsKey(FN_DELETE_AT)) {
            values.put(FN_DELETE_AT, DateUtils.DEFAULT_DELETE_AT);
        }
    }

    private void fillEntityInsertDefaults(Map<String, Object> entity) {
        String now = nowDateTime();
        if (entity.containsKey(FN_CREATE_AT)) {
            entity.put(FN_CREATE_AT, now);
        }
        if (entity.containsKey(FN_CREATOR)) {
            entity.put(FN_CREATOR, SessionCtx.getUserId());
        }
        if (entity.containsKey(FN_CREATOR_NAME)) {
            entity.put(FN_CREATOR_NAME, SessionCtx.getUserName());
        }
        if (entity.containsKey(FN_BU_ID)) {
            entity.put(FN_BU_ID, SessionCtx.getCurrentUser().getBuId());
        }
        if (entity.containsKey(FN_DEPT_ID)) {
            entity.put(FN_DEPT_ID, SessionCtx.getCurrentUser().getOrgId());
        }
        if (entity.containsKey(FN_DELETE_AT)) {
            entity.put(FN_DELETE_AT, DateUtils.DEFAULT_DELETE_AT);
        }
        fillUpdateDefaults(entity, entity);
    }

    private void fillUpdateDefaults(Map<String, Object> defaultEntityMap, Map<String, Object> values) {
        if (defaultEntityMap.containsKey(FN_UPDATE_AT)) {
            values.put(FN_UPDATE_AT, nowDateTime());
        }
        if (defaultEntityMap.containsKey(FN_UPDATER)) {
            values.put(FN_UPDATER, SessionCtx.getUserId());
        }
        if (defaultEntityMap.containsKey(FN_UPDATER_NAME)) {
            values.put(FN_UPDATER_NAME, SessionCtx.getUserName());
        }
    }

    private String nowDateTime() {
        return new SimpleDateFormat(DateUtils.DATETIME).format(new Date());
    }
}
