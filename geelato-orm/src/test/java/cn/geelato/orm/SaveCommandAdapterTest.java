package cn.geelato.orm;

import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.SaveCommandAdapter;
import cn.geelato.orm.support.TestUserEntity;
import cn.geelato.orm.value.ValueRefs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

        assertEquals(CommandType.Insert, command.getCommandType());
        assertEquals("TestUser", command.getEntityName());
        assertNotNull(command.getPK());
        assertEquals("Alice", command.getValueMap().get("name"));
        assertEquals("U1001", command.getValueMap().get("creator"));
        assertEquals("orm-tester", command.getValueMap().get("creatorName"));
        assertEquals("geelato", command.getValueMap().get("tenantCode"));
        assertEquals("BU1", command.getValueMap().get("buId"));
        assertEquals("ORG1", command.getValueMap().get("deptId"));
        assertEquals("U1001", command.getValueMap().get("updater"));
        assertNotNull(command.getValueMap().get("createAt"));
        assertNotNull(command.getValueMap().get("updateAt"));
        assertNotNull(command.getValueMap().get("deleteAt"));
        assertEquals(1, command.getCommands().size());
        assertEquals("$parent.id", command.getCommands().get(0).getValueMap().get("userId"));
    }

    @Test
    public void shouldBuildUpdateCommandFromPrimaryKey() {
        SaveCommand command = SaveCommandAdapter.fromUpdate(
                MetaFactory.update("TestUser")
                        .value("id", "19001")
                        .value("name", "Bob")
        );

        assertEquals(CommandType.Update, command.getCommandType());
        assertEquals("19001", command.getPK());
        assertNotNull(command.getWhere());
        assertEquals("Bob", command.getValueMap().get("name"));
        assertEquals("U1001", command.getValueMap().get("updater"));
        assertEquals("orm-tester", command.getValueMap().get("updaterName"));
        assertNotNull(command.getValueMap().get("updateAt"));
        assertNull(command.getValueMap().get("creator"));
        assertNull(command.getValueMap().get("tenantCode"));
    }
}
