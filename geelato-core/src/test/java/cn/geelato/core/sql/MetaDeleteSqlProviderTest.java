package cn.geelato.core.sql;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.DeleteMode;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Id;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 删除 SQL 生成的模式分支：LOGIC → update set del_status 等（值参数+条件参数）；
 * PHYSICAL → delete from（仅条件参数）。经 SqlManager.generateDeleteSql(Class,...) 端到端验证。
 */
class MetaDeleteSqlProviderTest {

    private static final MetaManager metaManager = MetaManager.singleInstance();
    private static final SqlManager sqlManager = SqlManager.singleInstance();

    @BeforeAll
    static void setUp() {
        metaManager.parseOne(DelSqlUserEntity.class);
        metaManager.parseOne(DelSqlPhyEntity.class);
        metaManager.parseOne(DelSqlPlainEntity.class);
    }

    @AfterAll
    static void tearDown() {
        metaManager.removeOne("DelSqlUser");
        metaManager.removeOne("DelSqlPhy");
        metaManager.removeOne("DelSqlPlain");
    }

    @Test
    void logicDeleteGeneratesUpdateSql() {
        BoundSql bs = sqlManager.generateDeleteSql(DelSqlUserEntity.class, whereTitle("x"));
        String sql = bs.getSql().toLowerCase();
        assertTrue(sql.startsWith("update"), bs.getSql());
        assertTrue(sql.contains("set"), bs.getSql());
        assertTrue(sql.contains("del_status=?"), bs.getSql());
        assertTrue(sql.contains("where title=?"), bs.getSql());
        // 值部分 5 个（delStatus/deleteAt/updateAt/updater/updaterName）+ 条件 1 个
        assertEquals(6, bs.getParams().length);
        assertEquals(6, bs.getTypes().length);
    }

    @Test
    void entityExplicitPhysicalBeatsGlobalLogic() {
        BoundSql bs = sqlManager.generateDeleteSql(DelSqlPhyEntity.class, whereTitle("x"));
        assertTrue(bs.getSql().toLowerCase().startsWith("delete from"), bs.getSql());
        assertEquals(1, bs.getParams().length);
    }

    @Test
    void logicWithoutDelStatusFieldHardFails() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sqlManager.generateDeleteSql(DelSqlPlainEntity.class, whereTitle("x")));
        assertTrue(ex.getMessage().contains("DelSqlPlain"));
    }

    @Test
    void entityNameVariantResolvesSameMode() {
        BoundSql bs = sqlManager.generateDeleteSql("DelSqlUser", whereTitle("x"));
        assertTrue(bs.getSql().toLowerCase().startsWith("update"), bs.getSql());
    }

    private FilterGroup whereTitle(String value) {
        FilterGroup fg = new FilterGroup();
        fg.addFilter("title", FilterGroup.Operator.eq, value);
        return fg;
    }

    @Getter
    @Setter
    @Entity(name = "DelSqlUser", table = "t_del_sql_user")
    public static class DelSqlUserEntity extends BaseEntity {
        @Col(name = "title", dataType = "VARCHAR", charMaxlength = 128)
        private String title;
    }

    @Getter
    @Setter
    @Entity(name = "DelSqlPhy", table = "t_del_sql_phy", deleteMode = DeleteMode.PHYSICAL)
    public static class DelSqlPhyEntity extends BaseEntity {
        @Col(name = "title", dataType = "VARCHAR", charMaxlength = 128)
        private String title;
    }

    @Getter
    @Setter
    @Entity(name = "DelSqlPlain", table = "t_del_sql_plain")
    public static class DelSqlPlainEntity {
        @Id
        @Col(name = "id", dataType = "VARCHAR", charMaxlength = 32)
        private String id;

        @Col(name = "title", dataType = "VARCHAR", charMaxlength = 128)
        private String title;
    }
}
