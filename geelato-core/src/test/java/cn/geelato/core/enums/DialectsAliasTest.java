package cn.geelato.core.enums;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 方言别名注册表：框架不内置具体引擎类型，由宿主/引擎适配器注册 dbType → 内置方言家族。
 */
class DialectsAliasTest {

    private static final String ALIAS = "dialect_alias_test_type";

    @AfterEach
    void cleanup() {
        Dialects.unregisterAlias(ALIAS);
    }

    @Test
    void aliasResolvesToRegisteredFamily() {
        assertNull(Dialects.lookUp(ALIAS));
        Dialects.registerAlias(ALIAS, Dialects.POSTGRESQL);
        assertEquals(Dialects.POSTGRESQL, Dialects.lookUp(ALIAS));
        assertEquals(Dialects.POSTGRESQL, Dialects.lookUp(ALIAS.toUpperCase()));
        assertEquals("org.postgresql.Driver", Dialects.lookUp(ALIAS).getDriver());
    }

    @Test
    void aliasRegistrationIsIdempotent() {
        Dialects.registerAlias(ALIAS, Dialects.POSTGRESQL);
        Dialects.registerAlias(ALIAS, Dialects.POSTGRESQL);
        assertEquals(Dialects.POSTGRESQL, Dialects.lookUp(ALIAS));
    }

    @Test
    void unregisterRemovesAlias() {
        Dialects.registerAlias(ALIAS, Dialects.POSTGRESQL);
        Dialects.unregisterAlias(ALIAS);
        assertNull(Dialects.lookUp(ALIAS));
    }

    @Test
    void builtInDialectsUnaffected() {
        assertEquals(Dialects.POSTGRESQL, Dialects.lookUp("postgresql"));
        assertEquals(Dialects.MYSQL, Dialects.lookUp("mysql"));
        assertNull(Dialects.lookUp("no_such_dialect_type"));
        assertNull(Dialects.lookUp(null));
    }
}
