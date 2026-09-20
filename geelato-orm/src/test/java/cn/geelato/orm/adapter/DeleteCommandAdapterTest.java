package cn.geelato.orm.adapter;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.lang.meta.DeleteMode;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaDelete;
import cn.geelato.orm.support.TestUserEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DSL 删除构建器到 DeleteCommand 的模式传递：
 * MetaFactory.delete(...) 默认（含 delStatus 字段实体）逻辑删除；
 * MetaFactory.physicalDelete(...) 调用级显式物理删除，不填充逻辑删除字段。
 */
class DeleteCommandAdapterTest {

    private static final MetaManager metaManager = MetaManager.singleInstance();

    @BeforeAll
    static void setUp() {
        metaManager.parseOne(TestUserEntity.class);
    }

    @AfterAll
    static void tearDown() {
        metaManager.removeOne("TestUser");
    }

    @Test
    void defaultDeleteResolvesLogicModeAndFillsValues() {
        MetaDelete delete = MetaFactory.delete(TestUserEntity.class)
                .where(Filter.eq("name", "x"));
        DeleteCommand command = DeleteCommandAdapter.from(delete);

        assertEquals(DeleteMode.LOGIC, command.getDeleteMode());
        assertEquals(1, command.getValueMap().get("delStatus"));
        assertTrue(command.getValueMap().containsKey("deleteAt"));
        assertEquals(command.getValueMap().keySet().size(), command.getFields().length);
    }

    @Test
    void physicalDeleteFactorySetsModeAndSkipsValueFill() {
        MetaDelete delete = MetaFactory.physicalDelete(TestUserEntity.class)
                .where(Filter.eq("name", "x"));
        DeleteCommand command = DeleteCommandAdapter.from(delete);

        assertEquals(DeleteMode.PHYSICAL, command.getDeleteMode());
        assertTrue(command.getValueMap() == null || command.getValueMap().isEmpty());
    }

    @Test
    void toSqlReflectsRequestedMode() {
        MetaDelete logic = MetaFactory.delete(TestUserEntity.class).where(Filter.eq("name", "x"));
        assertTrue(logic.toSql().toLowerCase().startsWith("update"), logic.toSql());

        MetaDelete physical = MetaFactory.physicalDelete(TestUserEntity.class)
                .where(Filter.eq("name", "x"));
        assertTrue(physical.toSql().toLowerCase().startsWith("delete from"), physical.toSql());
    }
}
