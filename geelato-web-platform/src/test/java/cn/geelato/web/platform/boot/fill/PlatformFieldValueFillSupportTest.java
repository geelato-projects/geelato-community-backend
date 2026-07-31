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
