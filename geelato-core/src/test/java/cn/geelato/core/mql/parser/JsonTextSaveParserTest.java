package cn.geelato.core.mql.parser;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.spi.MqlSaveFieldValueFillContext;
import cn.geelato.core.mql.spi.MqlSaveFieldValueFiller;
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

class JsonTextSaveParserTest {

    @BeforeEach
    void setUp() {
        MetaManager.singleInstance().parseOne(TestSaveEntity.class);
        User user = new User();
        user.setUserId("U1001");
        user.setUserName("core-tester");
        user.setBuId("BU1");
        user.setOrgId("ORG1");
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(new Tenant("geelato"));
    }

    @AfterEach
    void clearContext() {
        SecurityContext.clear();
        new BeansUtils().setApplicationContext(null);
    }

    @Test
    void shouldInvokeMqlSaveFieldValueFillerForInsert() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("filler", new TestMqlSaveFieldValueFiller());
        new BeansUtils().setApplicationContext(applicationContext);

        SaveCommand command = new JsonTextSaveParser().parse("{\"TestSaveEntity\":{\"name\":\"Alice\"}}", new cn.geelato.core.SessionCtx());

        assertEquals("TestSaveEntity", command.getEntityName());
        assertEquals("Alice", command.getValueMap().get("name"));
        assertEquals("U1001", command.getValueMap().get("creator"));
        assertEquals("geelato", command.getValueMap().get("tenantCode"));
        assertNotNull(command.getValueMap().get("createAt"));
        assertNotNull(command.getValueMap().get("deleteAt"));
    }

    @Test
    void shouldInvokeMqlSaveFieldValueFillerForUpdate() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("filler", new TestMqlSaveFieldValueFiller());
        new BeansUtils().setApplicationContext(applicationContext);

        SaveCommand command = new JsonTextSaveParser().parse("{\"TestSaveEntity\":{\"id\":\"1001\",\"name\":\"Bob\"}}", new cn.geelato.core.SessionCtx());

        assertEquals("1001", command.getPK());
        assertEquals("Bob", command.getValueMap().get("name"));
        assertEquals("U1001", command.getValueMap().get("updater"));
        assertEquals("core-tester", command.getValueMap().get("updaterName"));
        assertNotNull(command.getValueMap().get("updateAt"));
    }

    private static class TestMqlSaveFieldValueFiller implements MqlSaveFieldValueFiller {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void fill(MqlSaveFieldValueFillContext context) {
            if (context.getCommandType() == cn.geelato.core.mql.command.CommandType.Insert) {
                context.getTargetValueMap().put("createAt", "2026-07-16 00:00:00");
                context.getTargetValueMap().put("creator", "U1001");
                context.getTargetValueMap().put("creatorName", "core-tester");
                context.getTargetValueMap().put("tenantCode", "geelato");
                context.getTargetValueMap().put("buId", "BU1");
                context.getTargetValueMap().put("deptId", "ORG1");
                context.getTargetValueMap().put("updateAt", "2026-07-16 00:00:00");
                context.getTargetValueMap().put("updater", "U1001");
                context.getTargetValueMap().put("updaterName", "core-tester");
                context.getTargetValueMap().put("deleteAt", "9999-12-31 00:00:00");
            } else {
                context.getTargetValueMap().put("updateAt", "2026-07-16 00:00:00");
                context.getTargetValueMap().put("updater", "U1001");
                context.getTargetValueMap().put("updaterName", "core-tester");
            }
        }
    }
}
