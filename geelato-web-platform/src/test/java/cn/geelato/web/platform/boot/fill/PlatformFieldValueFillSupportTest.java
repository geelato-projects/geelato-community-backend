package cn.geelato.web.platform.boot.fill;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.spi.EntitySaveFieldValueFillContext;
import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.spi.MqlSaveFieldValueFillContext;
import cn.geelato.orm.spi.FluentSaveFieldValueFillContext;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.utils.DateUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformFieldValueFillSupportTest {

    private static final String FN_CREATE_AT = "createAt";
    private static final String FN_CREATOR = "creator";
    private static final String FN_CREATOR_NAME = "creatorName";
    private static final String FN_UPDATE_AT = "updateAt";
    private static final String FN_UPDATER = "updater";
    private static final String FN_UPDATER_NAME = "updaterName";
    private static final String FN_TENANT_CODE = ColumnDefault.TENANT_CODE_FIELD;
    private static final String FN_BU_ID = "buId";
    private static final String FN_DEPT_ID = "deptId";
    private static final String FN_DELETE_AT = ColumnDefault.DELETE_AT_FIELD;

    private final PlatformFieldValueFillSupport support = new PlatformFieldValueFillSupport();

    @AfterEach
    void clearContext() {
        SecurityContext.clear();
    }

    @Test
    void shouldApplyFluentInsertDefaultsFromSecurityContext() {
        User user = new User();
        user.setUserId("u-1");
        user.setUserName("Alice");
        user.setBuId("bu-1");
        user.setOrgId("org-1");
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(new Tenant("tenant-a"));

        Map<String, Object> values = new LinkedHashMap<>();

        support.applyFluentDefaults(new FluentSaveFieldValueFillContext(
                "demo", CommandType.Insert, null, defaultFields(), values
        ));

        assertNotNull(values.get(FN_CREATE_AT));
        assertEquals("u-1", values.get(FN_CREATOR));
        assertEquals("Alice", values.get(FN_CREATOR_NAME));
        assertEquals("tenant-a", values.get(FN_TENANT_CODE));
        assertEquals("bu-1", values.get(FN_BU_ID));
        assertEquals("org-1", values.get(FN_DEPT_ID));
        assertNotNull(values.get(FN_UPDATE_AT));
        assertEquals("u-1", values.get(FN_UPDATER));
        assertEquals("Alice", values.get(FN_UPDATER_NAME));
        assertEquals(DateUtils.DEFAULT_DELETE_AT, values.get(FN_DELETE_AT));
    }

    @Test
    void shouldSkipOrgDefaultsWhenCurrentUserMissingForMqlInsert() {
        Map<String, Object> values = new LinkedHashMap<>();

        support.applyMqlDefaults(new MqlSaveFieldValueFillContext(
                "demo", CommandType.Insert, null, defaultFields(), values, null
        ));

        assertNotNull(values.get(FN_CREATE_AT));
        assertTrue(values.containsKey(FN_CREATOR));
        assertNull(values.get(FN_CREATOR));
        assertTrue(values.containsKey(FN_TENANT_CODE));
        assertNull(values.get(FN_TENANT_CODE));
        assertFalse(values.containsKey(FN_BU_ID));
        assertFalse(values.containsKey(FN_DEPT_ID));
        assertNotNull(values.get(FN_UPDATE_AT));
        assertEquals(DateUtils.DEFAULT_DELETE_AT, values.get(FN_DELETE_AT));
    }

    @Test
    void shouldKeepExistingOrgFieldsWhenCurrentUserMissingForEntityInsert() {
        Map<String, Object> entity = defaultFields();
        entity.put(FN_BU_ID, "manual-bu");
        entity.put(FN_DEPT_ID, "manual-dept");

        support.applyEntityDefaults(new EntitySaveFieldValueFillContext(
                "demo", CommandType.Insert, null, entity, entity, null, null
        ));

        assertNotNull(entity.get(FN_CREATE_AT));
        assertNull(entity.get(FN_CREATOR));
        assertEquals("manual-bu", entity.get(FN_BU_ID));
        assertEquals("manual-dept", entity.get(FN_DEPT_ID));
        assertNotNull(entity.get(FN_UPDATE_AT));
        assertNull(entity.get(FN_UPDATER));
        assertEquals(DateUtils.DEFAULT_DELETE_AT, entity.get(FN_DELETE_AT));
    }

    @Test
    void shouldOverrideCallerSuppliedAuditFieldsOnInsertEvenWithoutDefaultMeta() {
        // 安全缺陷回归：调用方在报文中手工指定 creator/updater 时，必须被当前登录用户覆盖，
        // 且不依赖 defaultEntityMap 是否收录该列（元数据驱动链路 defaultEntityMap 往往取不到列）。
        User user = new User();
        user.setUserId("real-user");
        user.setUserName("Real User");
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(new Tenant("tenant-a"));

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(FN_CREATOR, "forged-creator");
        values.put(FN_CREATOR_NAME, "Forged Creator");
        values.put(FN_UPDATER, "forged-updater");
        values.put(FN_TENANT_CODE, "forged-tenant");

        // defaultEntityMap 传空 Map（模拟元数据取不到列），entityMeta 承载字段存在性
        support.applyMqlDefaults(new MqlSaveFieldValueFillContext(
                "demo", CommandType.Insert, new AuditFieldEntityMeta(), java.util.Collections.emptyMap(), values, null
        ));

        assertEquals("real-user", values.get(FN_CREATOR));
        assertEquals("Real User", values.get(FN_CREATOR_NAME));
        assertEquals("real-user", values.get(FN_UPDATER));
        assertEquals("tenant-a", values.get(FN_TENANT_CODE));
    }

    @Test
    void shouldDropCallerSuppliedCreationFieldsOnUpdate() {
        User user = new User();
        user.setUserId("real-user");
        user.setUserName("Real User");
        SecurityContext.setCurrentUser(user);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(FN_CREATOR, "forged-creator");
        values.put(FN_CREATE_AT, "2000-01-01 00:00:00");
        values.put(FN_UPDATER, "forged-updater");
        values.put(FN_TENANT_CODE, "forged-tenant");

        support.applyMqlDefaults(new MqlSaveFieldValueFillContext(
                "demo", CommandType.Update, new AuditFieldEntityMeta(), java.util.Collections.emptyMap(), values, null
        ));

        assertFalse(values.containsKey(FN_CREATOR));
        assertFalse(values.containsKey(FN_CREATE_AT));
        assertFalse(values.containsKey(FN_TENANT_CODE));
        assertEquals("real-user", values.get(FN_UPDATER));
    }

    /** 测试用元数据：字段存在性判定路径，containsField 对审计字段返回 true。 */
    private static class AuditFieldEntityMeta extends cn.geelato.core.meta.model.entity.EntityMeta {
        AuditFieldEntityMeta() {
            super();
        }

        @Override
        public boolean containsField(String fieldName) {
            return FN_CREATE_AT.equals(fieldName) || FN_CREATOR.equals(fieldName)
                    || FN_CREATOR_NAME.equals(fieldName) || FN_TENANT_CODE.equals(fieldName)
                    || FN_BU_ID.equals(fieldName) || FN_DEPT_ID.equals(fieldName)
                    || FN_UPDATE_AT.equals(fieldName) || FN_UPDATER.equals(fieldName)
                    || FN_UPDATER_NAME.equals(fieldName) || FN_DELETE_AT.equals(fieldName);
        }
    }

    private Map<String, Object> defaultFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(FN_CREATE_AT, null);
        fields.put(FN_CREATOR, null);
        fields.put(FN_CREATOR_NAME, null);
        fields.put(FN_TENANT_CODE, null);
        fields.put(FN_BU_ID, null);
        fields.put(FN_DEPT_ID, null);
        fields.put(FN_UPDATE_AT, null);
        fields.put(FN_UPDATER, null);
        fields.put(FN_UPDATER_NAME, null);
        fields.put(FN_DELETE_AT, null);
        return fields;
    }
}
