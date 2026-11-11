package cn.geelato.web.platform.boot.fill;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.spi.EntitySaveFieldValueFillContext;
import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.spi.MqlSaveFieldValueFillContext;
import cn.geelato.orm.spi.FluentSaveFieldValueFillContext;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
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
            fillMqlInsertDefaults(context);
        } else if (context.getCommandType() == CommandType.Update) {
            fillUpdateDefaults(context.getEntityMeta(), context.getTargetValueMap());
        }
    }

    void applyFluentDefaults(FluentSaveFieldValueFillContext context) {
        if (context == null || context.getCommandType() == null) {
            return;
        }
        if (context.getCommandType() == CommandType.Insert) {
            fillFluentInsertDefaults(context);
        } else if (context.getCommandType() == CommandType.Update) {
            fillUpdateDefaults(context.getEntityMeta(), context.getTargetValueMap());
        }
    }

    void applyEntityDefaults(EntitySaveFieldValueFillContext context) {
        if (context == null || context.getCommandType() == null) {
            return;
        }
        if (context.getCommandType() == CommandType.Insert) {
            fillEntityInsertDefaults(context.getEntityMeta(), context.getTargetValueMap());
        } else if (context.getCommandType() == CommandType.Update) {
            fillUpdateDefaults(context.getEntityMeta(), context.getTargetValueMap());
        }
    }

    /**
     * 审计字段可写性守卫：以实体元数据中字段是否存在为准，而不是默认值元数据。
     * <p>
     * defaultEntityMap（MetaManager.newDefaultEntityMap）只收录“有非空默认值且状态启用”的列：
     * 注解驱动实体中 String 审计字段默认为 null 不入表，元数据驱动（platform_dev_column）链路
     * 因 enable_status 经 Boolean.parseBoolean("1") 恒为 false 而整体取不到列。
     * 若以 defaultEntityMap.containsKey 作为覆盖前提，调用方在 MQL/Fluent 报文中手工指定的
     * creator/updater 等审计字段会绕过填充器直接落库（安全缺陷），故这里改为按 EntityMeta
     * 字段存在性判定；EntityMeta 缺失时才退化为默认值元数据存在性。
     */
    private static boolean changeIfPresent(EntityMeta entityMeta, Map<String, Object> defaultEntityMap,
                                           Map<String, Object> values, String fieldName, Object value) {
        boolean present = entityMeta != null
                ? entityMeta.containsField(fieldName)
                : defaultEntityMap != null && defaultEntityMap.containsKey(fieldName);
        if (present) {
            values.put(fieldName, value);
        }
        return present;
    }

    private void fillMqlInsertDefaults(MqlSaveFieldValueFillContext context) {
        EntityMeta entityMeta = context.getEntityMeta();
        Map<String, Object> defaultEntityMap = context.getDefaultEntityMap();
        Map<String, Object> values = context.getTargetValueMap();
        String now = nowDateTime();
        User currentUser = SecurityContext.getCurrentUser();
        Tenant currentTenant = SecurityContext.getCurrentTenant();
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_CREATE_AT, now);
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_CREATOR, getUserId(currentUser));
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_CREATOR_NAME, getUserName(currentUser));
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_TENANT_CODE, getTenantCode(currentTenant));
        fillOrgDefaults(entityMeta, defaultEntityMap, values, currentUser);
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_UPDATE_AT, now);
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_UPDATER, getUserId(currentUser));
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_UPDATER_NAME, getUserName(currentUser));
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_DELETE_AT, DateUtils.DEFAULT_DELETE_AT);
    }

    private void fillFluentInsertDefaults(FluentSaveFieldValueFillContext context) {
        EntityMeta entityMeta = context.getEntityMeta();
        Map<String, Object> defaultEntityMap = context.getDefaultEntityMap();
        Map<String, Object> values = context.getTargetValueMap();
        String now = nowDateTime();
        User currentUser = SecurityContext.getCurrentUser();
        Tenant currentTenant = SecurityContext.getCurrentTenant();
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_CREATE_AT, now);
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_CREATOR, getUserId(currentUser));
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_CREATOR_NAME, getUserName(currentUser));
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_TENANT_CODE, getTenantCode(currentTenant));
        fillOrgDefaults(entityMeta, defaultEntityMap, values, currentUser);
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_UPDATE_AT, now);
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_UPDATER, getUserId(currentUser));
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_UPDATER_NAME, getUserName(currentUser));
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_DELETE_AT, DateUtils.DEFAULT_DELETE_AT);
    }

    private void fillEntityInsertDefaults(EntityMeta entityMeta, Map<String, Object> entity) {
        String now = nowDateTime();
        User currentUser = SecurityContext.getCurrentUser();
        changeIfPresent(entityMeta, entity, entity, FN_CREATE_AT, now);
        changeIfPresent(entityMeta, entity, entity, FN_CREATOR, getUserId(currentUser));
        changeIfPresent(entityMeta, entity, entity, FN_CREATOR_NAME, getUserName(currentUser));
        fillOrgDefaults(entityMeta, entity, entity, currentUser);
        changeIfPresent(entityMeta, entity, entity, FN_DELETE_AT, DateUtils.DEFAULT_DELETE_AT);
        // 插入链路补齐更新审计字段，但不可丢弃刚写入的创建审计字段
        applyUpdateAuditFields(entityMeta, entity, entity);
    }

    private void fillUpdateDefaults(EntityMeta entityMeta, Map<String, Object> values) {
        // 实体链路的上下文不单独携带默认值元数据，退化为目标值 Map 自身
        Map<String, Object> defaultEntityMap = entityMeta == null ? values : null;
        // 更新链路禁止篡改创建审计字段与租户归属：调用方传入的 createAt/creator/creatorName/tenantCode 一律丢弃，
        // 且不再纳入 updateFields，避免把创建人/创建时间/所属租户改写成伪造值（保持库中原值）。
        values.remove(FN_CREATE_AT);
        values.remove(FN_CREATOR);
        values.remove(FN_CREATOR_NAME);
        values.remove(FN_TENANT_CODE);
        applyUpdateAuditFields(entityMeta, defaultEntityMap, values);
    }

    /** 仅写入更新审计字段（updateAt/updater/updaterName），供更新链路与插入链路末尾复用。 */
    private void applyUpdateAuditFields(EntityMeta entityMeta, Map<String, Object> defaultEntityMap,
                                        Map<String, Object> values) {
        User currentUser = SecurityContext.getCurrentUser();
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_UPDATE_AT, nowDateTime());
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_UPDATER, getUserId(currentUser));
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_UPDATER_NAME, getUserName(currentUser));
    }

    private void fillOrgDefaults(EntityMeta entityMeta, Map<String, Object> defaultEntityMap,
                                 Map<String, Object> values, User currentUser) {
        if (currentUser == null) {
            return;
        }
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_BU_ID, currentUser.getBuId());
        changeIfPresent(entityMeta, defaultEntityMap, values, FN_DEPT_ID, currentUser.getOrgId());
    }

    private String getUserId(User currentUser) {
        return currentUser == null ? null : currentUser.getUserId();
    }

    private String getUserName(User currentUser) {
        return currentUser == null ? null : currentUser.getUserName();
    }

    private String getTenantCode(Tenant currentTenant) {
        return currentTenant == null ? null : currentTenant.getCode();
    }

    private String nowDateTime() {
        return new SimpleDateFormat(DateUtils.DATETIME).format(new Date());
    }
}
