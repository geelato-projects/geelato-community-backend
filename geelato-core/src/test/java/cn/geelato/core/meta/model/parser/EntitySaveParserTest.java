package cn.geelato.core.meta.model.parser;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.spi.EntitySaveFieldValueFillContext;
import cn.geelato.core.meta.spi.EntitySaveFieldValueFiller;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.test.support.TestSaveEntity;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntitySaveParserTest {

    @BeforeEach
    void setUp() {
        MetaManager.singleInstance().parseOne(TestSaveEntity.class);
        User user = new User();
        user.setUserId("U2001");
        user.setUserName("entity-tester");
        user.setBuId("BU2");
        user.setOrgId("ORG2");
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(new Tenant("entity-tenant"));
    }

    @AfterEach
    void clearContext() {
        SecurityContext.clear();
        new BeansUtils().setApplicationContext(null);
    }

    @Test
    void shouldInvokeEntitySaveFieldValueFillerForInsert() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("filler", new TestEntitySaveFieldValueFiller());
        new BeansUtils().setApplicationContext(applicationContext);

        TestSaveEntity entity = new TestSaveEntity();
        entity.setName("Alice");

        SaveCommand command = new EntitySaveParser().parse(entity, new SessionCtx());

        assertEquals("TestSaveEntity", command.getEntityName());
        assertEquals("Alice", command.getValueMap().get("name"));
        assertEquals("U2001", command.getValueMap().get("creator"));
        assertEquals("entity-tester", command.getValueMap().get("creatorName"));
        assertEquals("BU2", command.getValueMap().get("buId"));
        assertEquals("ORG2", command.getValueMap().get("deptId"));
        assertNotNull(command.getValueMap().get("createAt"));
        assertNotNull(command.getValueMap().get("deleteAt"));
        assertNull(command.getValueMap().get("tenantCode"));
    }

    @Test
    void shouldInvokeEntitySaveFieldValueFillerForUpdate() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("filler", new TestEntitySaveFieldValueFiller());
        new BeansUtils().setApplicationContext(applicationContext);

        TestSaveEntity entity = new TestSaveEntity();
        entity.setId("1001");
        entity.setName("Bob");

        SaveCommand command = new EntitySaveParser().parse(entity, new SessionCtx());

        assertEquals("1001", command.getWhere().getFilters().get(0).getValue());
        assertEquals("Bob", command.getValueMap().get("name"));
        assertEquals("U2001", command.getValueMap().get("updater"));
        assertEquals("entity-tester", command.getValueMap().get("updaterName"));
        assertNotNull(command.getValueMap().get("updateAt"));
    }

    private static class TestEntitySaveFieldValueFiller implements EntitySaveFieldValueFiller {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void fill(EntitySaveFieldValueFillContext context) {
            if (context.getCommandType() == cn.geelato.core.mql.command.CommandType.Insert) {
                context.getTargetValueMap().put("createAt", "2026-07-16 00:00:00");
                context.getTargetValueMap().put("creator", "U2001");
                context.getTargetValueMap().put("creatorName", "entity-tester");
                context.getTargetValueMap().put("buId", "BU2");
                context.getTargetValueMap().put("deptId", "ORG2");
                context.getTargetValueMap().put("updateAt", "2026-07-16 00:00:00");
                context.getTargetValueMap().put("updater", "U2001");
                context.getTargetValueMap().put("updaterName", "entity-tester");
                context.getTargetValueMap().put("deleteAt", "9999-12-31 00:00:00");
            } else {
                context.getTargetValueMap().put("updateAt", "2026-07-16 00:00:00");
                context.getTargetValueMap().put("updater", "U2001");
                context.getTargetValueMap().put("updaterName", "entity-tester");
            }
        }
    }
}
