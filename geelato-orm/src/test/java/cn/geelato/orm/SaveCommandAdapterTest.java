package cn.geelato.orm;

import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.SaveCommandAdapter;
import cn.geelato.orm.support.TestUserEntity;
import cn.geelato.orm.value.ValueRefs;
import org.junit.Assert;
import org.junit.Test;

public class SaveCommandAdapterTest extends OrmTestSupport {

    @Test
    public void shouldBuildInsertCommandWithNestedChild() {
        SaveCommand command = SaveCommandAdapter.fromInsert(
                MetaFactory.insert(TestUserEntity.class)
                        .value("name", "Alice")
                        .child("TestOrder", child -> child
                                .value("userId", ValueRefs.parent("id"))
                                .value("code", "SO-001"))
        );

        Assert.assertEquals(CommandType.Insert, command.getCommandType());
        Assert.assertEquals("TestUser", command.getEntityName());
        Assert.assertNotNull(command.getPK());
        Assert.assertEquals("Alice", command.getValueMap().get("name"));
        Assert.assertEquals("U1001", command.getValueMap().get("creator"));
        Assert.assertEquals("orm-tester", command.getValueMap().get("creatorName"));
        Assert.assertEquals("geelato", command.getValueMap().get("tenantCode"));
        Assert.assertEquals("BU1", command.getValueMap().get("buId"));
        Assert.assertEquals("ORG1", command.getValueMap().get("deptId"));
        Assert.assertEquals("U1001", command.getValueMap().get("updater"));
        Assert.assertNotNull(command.getValueMap().get("createAt"));
        Assert.assertNotNull(command.getValueMap().get("updateAt"));
        Assert.assertNotNull(command.getValueMap().get("deleteAt"));
        Assert.assertEquals(1, command.getCommands().size());
        Assert.assertEquals("$parent.id", command.getCommands().get(0).getValueMap().get("userId"));
    }

    @Test
    public void shouldBuildUpdateCommandFromPrimaryKey() {
        SaveCommand command = SaveCommandAdapter.fromUpdate(
                MetaFactory.update("TestUser")
                        .value("id", "19001")
                        .value("name", "Bob")
        );

        Assert.assertEquals(CommandType.Update, command.getCommandType());
        Assert.assertEquals("19001", command.getPK());
        Assert.assertNotNull(command.getWhere());
        Assert.assertEquals("Bob", command.getValueMap().get("name"));
        Assert.assertEquals("U1001", command.getValueMap().get("updater"));
        Assert.assertEquals("orm-tester", command.getValueMap().get("updaterName"));
        Assert.assertNotNull(command.getValueMap().get("updateAt"));
        Assert.assertNull(command.getValueMap().get("creator"));
        Assert.assertNull(command.getValueMap().get("tenantCode"));
    }
}
