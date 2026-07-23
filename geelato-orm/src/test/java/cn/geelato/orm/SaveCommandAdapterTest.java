package cn.geelato.orm;

import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.orm.spi.FluentSaveFieldValueFillContext;
import cn.geelato.orm.spi.FluentSaveFieldValueFiller;
import cn.geelato.orm.adapter.SaveCommandAdapter;
import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.TestUserEntity;
import cn.geelato.orm.value.ValueRefs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SaveCommandAdapterTest extends OrmTestSupport {

    @AfterEach
    void clearContext() {
        new BeansUtils().setApplicationContext(null);
    }

    @Test
    public void shouldBuildInsertCommandWithNestedChild() {
        registerEnabledFiller();
        SaveCommand command = SaveCommandAdapter.fromInsert(
                MetaFactory.insert(TestUserEntity.class)
                        .useDataSource("archive")
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
        assertEquals("archive", command.getConnectId());
        assertEquals(1, command.getCommands().size());
        assertEquals("$parent.id", command.getCommands().get(0).getValueMap().get("userId"));
    }

    @Test
    public void shouldBuildUpdateCommandFromPrimaryKey() {
        registerEnabledFiller();
        SaveCommand command = SaveCommandAdapter.fromUpdate(
                MetaFactory.update("TestUser")
                        .useDataSource("analytics")
                        .value("id", "19001")
                        .value("name", "Bob")
        );

        assertEquals(CommandType.Update, command.getCommandType());
        assertEquals("19001", command.getPK());
        assertNotNull(command.getWhere());
        assertEquals("analytics", command.getConnectId());
        assertEquals("Bob", command.getValueMap().get("name"));
        assertEquals("U1001", command.getValueMap().get("updater"));
        assertEquals("orm-tester", command.getValueMap().get("updaterName"));
        assertNotNull(command.getValueMap().get("updateAt"));
        assertNull(command.getValueMap().get("creator"));
        assertNull(command.getValueMap().get("tenantCode"));
    }

    private void registerEnabledFiller() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("filler", new FluentSaveFieldValueFiller() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void fill(FluentSaveFieldValueFillContext context) {
                if (context.getCommandType() == CommandType.Insert) {
                    context.getTargetValueMap().put("createAt", "2026-07-16 00:00:00");
                    context.getTargetValueMap().put("creator", "U1001");
                    context.getTargetValueMap().put("creatorName", "orm-tester");
                    context.getTargetValueMap().put("tenantCode", "geelato");
                    context.getTargetValueMap().put("buId", "BU1");
                    context.getTargetValueMap().put("deptId", "ORG1");
                    context.getTargetValueMap().put("updateAt", "2026-07-16 00:00:00");
                    context.getTargetValueMap().put("updater", "U1001");
                    context.getTargetValueMap().put("updaterName", "orm-tester");
                    context.getTargetValueMap().put("deleteAt", "9999-12-31 00:00:00");
                } else {
                    context.getTargetValueMap().put("updateAt", "2026-07-16 00:00:00");
                    context.getTargetValueMap().put("updater", "U1001");
                    context.getTargetValueMap().put("updaterName", "orm-tester");
                }
            }
        });
        new BeansUtils().setApplicationContext(applicationContext);
    }
}
