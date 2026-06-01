package cn.geelato.orm;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.mql.command.CommandType;
import cn.geelato.orm.fill.DefaultSaveDefaultValueFiller;
import cn.geelato.orm.fill.SaveDefaultValueContext;
import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.TestUserEntity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DefaultSaveDefaultValueFillerTest extends OrmTestSupport {

    @Test
    public void shouldFillInsertDefaultsAlignedWithMql() {
        Map<String, Object> values = new HashMap<>();
        values.put("name", "Alice");

        new DefaultSaveDefaultValueFiller().fill(new SaveDefaultValueContext(
                "TestUser",
                CommandType.Insert,
                MetaManager.singleInstance().get(TestUserEntity.class),
                MetaManager.singleInstance().newDefaultEntityMap("TestUser"),
                values
        ));

        assertEquals("U1001", values.get("creator"));
        assertEquals("orm-tester", values.get("creatorName"));
        assertEquals("geelato", values.get("tenantCode"));
        assertEquals("BU1", values.get("buId"));
        assertEquals("ORG1", values.get("deptId"));
        assertEquals("U1001", values.get("updater"));
        assertEquals("orm-tester", values.get("updaterName"));
        assertNotNull(values.get("createAt"));
        assertNotNull(values.get("updateAt"));
        assertNotNull(values.get("deleteAt"));
    }

    @Test
    public void shouldFillOnlyUpdateDefaultsForUpdateCommand() {
        Map<String, Object> values = new HashMap<>();
        values.put("name", "Bob");

        new DefaultSaveDefaultValueFiller().fill(new SaveDefaultValueContext(
                "TestUser",
                CommandType.Update,
                MetaManager.singleInstance().get(TestUserEntity.class),
                MetaManager.singleInstance().newDefaultEntityMap("TestUser"),
                values
        ));

        assertNull(values.get("creator"));
        assertNull(values.get("tenantCode"));
        assertEquals("U1001", values.get("updater"));
        assertEquals("orm-tester", values.get("updaterName"));
        assertNotNull(values.get("updateAt"));
    }
}
