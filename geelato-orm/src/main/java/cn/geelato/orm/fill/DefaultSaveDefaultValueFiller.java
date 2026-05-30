package cn.geelato.orm.fill;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.mql.command.CommandType;
import cn.geelato.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * Default filler aligned with the current MQL save parser behavior.
 */
public class DefaultSaveDefaultValueFiller implements SaveDefaultValueFiller {
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

    @Override
    public void fill(SaveDefaultValueContext context) {
        if (context == null || context.getCommandType() == null || context.getEntityDefaults() == null || context.getValueMap() == null) {
            return;
        }
        if (context.getCommandType() == CommandType.Insert) {
            fillInsertDefaults(context.getEntityDefaults(), context.getValueMap());
        } else if (context.getCommandType() == CommandType.Update) {
            fillUpdateDefaults(context.getEntityDefaults(), context.getValueMap());
        }
    }

    private void fillInsertDefaults(Map<String, Object> entityDefaults, Map<String, Object> values) {
        String now = nowDateTime();
        if (entityDefaults.containsKey(FN_CREATE_AT)) {
            values.put(FN_CREATE_AT, now);
        }
        if (entityDefaults.containsKey(FN_CREATOR)) {
            values.put(FN_CREATOR, SessionCtx.getUserId());
        }
        if (entityDefaults.containsKey(FN_CREATOR_NAME)) {
            values.put(FN_CREATOR_NAME, SessionCtx.getUserName());
        }
        if (entityDefaults.containsKey(FN_TENANT_CODE)) {
            values.put(FN_TENANT_CODE, SessionCtx.getCurrentTenantCode());
        }
        if (entityDefaults.containsKey(FN_BU_ID) && SessionCtx.getCurrentUser() != null) {
            values.put(FN_BU_ID, SessionCtx.getCurrentUser().getBuId());
        }
        if (entityDefaults.containsKey(FN_DEPT_ID) && SessionCtx.getCurrentUser() != null) {
            values.put(FN_DEPT_ID, SessionCtx.getCurrentUser().getOrgId());
        }
        if (entityDefaults.containsKey(FN_UPDATE_AT)) {
            values.put(FN_UPDATE_AT, now);
        }
        if (entityDefaults.containsKey(FN_UPDATER)) {
            values.put(FN_UPDATER, SessionCtx.getUserId());
        }
        if (entityDefaults.containsKey(FN_UPDATER_NAME)) {
            values.put(FN_UPDATER_NAME, SessionCtx.getUserName());
        }
        if (entityDefaults.containsKey(FN_DELETE_AT)) {
            values.put(FN_DELETE_AT, DateUtils.DEFAULT_DELETE_AT);
        }
    }

    private void fillUpdateDefaults(Map<String, Object> entityDefaults, Map<String, Object> values) {
        if (entityDefaults.containsKey(FN_UPDATE_AT)) {
            values.put(FN_UPDATE_AT, nowDateTime());
        }
        if (entityDefaults.containsKey(FN_UPDATER)) {
            values.put(FN_UPDATER, SessionCtx.getUserId());
        }
        if (entityDefaults.containsKey(FN_UPDATER_NAME)) {
            values.put(FN_UPDATER_NAME, SessionCtx.getUserName());
        }
    }

    private String nowDateTime() {
        return new SimpleDateFormat(DateUtils.DATETIME).format(new Date());
    }
}
