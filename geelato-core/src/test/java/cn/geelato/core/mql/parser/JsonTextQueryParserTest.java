package cn.geelato.core.mql.parser;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.spi.MqlQueryFilterInjector;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonTextQueryParserTest {

    @BeforeEach
    void setUpMetadata() {
        MetaManager.singleInstance().parseOne(TableMeta.class);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContext.clear();
        new BeansUtils().setApplicationContext(null);
    }

    @Test
    void shouldInvokeRuntimeInjectorAfterParsing() {
        User user = new User();
        user.setUserId("u1");
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(new Tenant("tenant-a"));

        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("injector", new MqlQueryFilterInjector() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void inject(QueryCommand command) {
                command.getWhere().addFilter("tenantCode", "tenant-a");
                command.setOriginalWhere("creator='u1'");
            }
        });
        new BeansUtils().setApplicationContext(applicationContext);

        JsonTextQueryParser parser = new JsonTextQueryParser();
        QueryCommand command = parser.parse("{\"platform_dev_table\":{}}");

        assertNotNull(command.getWhere());
        assertEquals("tenantCode", command.getWhere().getFilters().get(0).getField());
        assertEquals("tenant-a", command.getWhere().getFilters().get(0).getValue());
        assertEquals("creator='u1'", command.getOriginalWhere());
    }
}
