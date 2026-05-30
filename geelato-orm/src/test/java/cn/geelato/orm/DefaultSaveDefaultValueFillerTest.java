package cn.geelato.orm;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.mql.command.CommandType;
import cn.geelato.orm.fill.DefaultSaveDefaultValueFiller;
import cn.geelato.orm.fill.SaveDefaultValueContext;
import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.TestUserEntity;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

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

        Assert.assertEquals("U1001", values.get("creator"));
        Assert.assertEquals("orm-tester", values.get("creatorName"));
        Assert.assertEquals("geelato", values.get("tenantCode"));
        Assert.assertEquals("BU1", values.get("buId"));
        Assert.assertEquals("ORG1", values.get("deptId"));
        Assert.assertEquals("U1001", values.get("updater"));
        Assert.assertEquals("orm-tester", values.get("updaterName"));
        Assert.assertNotNull(values.get("createAt"));
        Assert.assertNotNull(values.get("updateAt"));
        Assert.assertNotNull(values.get("deleteAt"));
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

        Assert.assertNull(values.get("creator"));
        Assert.assertNull(values.get("tenantCode"));
        Assert.assertEquals("U1001", values.get("updater"));
        Assert.assertEquals("orm-tester", values.get("updaterName"));
        Assert.assertNotNull(values.get("updateAt"));
    }
}
