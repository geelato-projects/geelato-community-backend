package cn.geelato.core.mql.parser;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.DeleteMode;
import cn.geelato.lang.meta.Entity;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MQL JSON 删除链路的删除模式：默认逻辑删除（填充 delStatus 等），
 * @physicalDelete true 切物理删除，非法值走 validator 报错链。
 */
class JsonTextDeleteParserTest {

    private static final MetaManager metaManager = MetaManager.singleInstance();
    private static final JsonTextDeleteParser parser = new JsonTextDeleteParser();

    static {
        metaManager.parseOne(DelMqlUserEntity.class);
    }

    @AfterAll
    static void tearDown() {
        metaManager.removeOne("DelMqlUser");
    }

    @Test
    void parseDefaultsToLogicDelete() {
        DeleteCommand command = parser.parse("{\"DelMqlUser\":{\"title\":\"x\"}}", new SessionCtx());
        assertEquals(DeleteMode.LOGIC, command.getDeleteMode());
        assertEquals(1, command.getValueMap().get("delStatus"));
        assertTrue(command.getValueMap().containsKey("deleteAt"));
    }

    @Test
    void physicalDeleteKeywordSwitchesMode() {
        DeleteCommand command = parser.parse(
                "{\"DelMqlUser\":{\"title\":\"x\",\"@physicalDelete\":true}}", new SessionCtx());
        assertEquals(DeleteMode.PHYSICAL, command.getDeleteMode());
        assertTrue(command.getValueMap() == null || command.getValueMap().isEmpty());
        assertEquals(0, command.getFields().length);
    }

    @Test
    void physicalDeleteKeywordFalseKeepsLogic() {
        DeleteCommand command = parser.parse(
                "{\"DelMqlUser\":{\"title\":\"x\",\"@physicalDelete\":false}}", new SessionCtx());
        assertEquals(DeleteMode.LOGIC, command.getDeleteMode());
        assertEquals(1, command.getValueMap().get("delStatus"));
    }

    @Test
    void physicalDeleteKeywordInvalidValueFails() {
        assertThrows(RuntimeException.class, () -> parser.parse(
                "{\"DelMqlUser\":{\"title\":\"x\",\"@physicalDelete\":\"yes\"}}", new SessionCtx()));
    }

    @Getter
    @Setter
    @Entity(name = "DelMqlUser", table = "t_del_mql_user")
    public static class DelMqlUserEntity extends BaseEntity {
        @Col(name = "title", dataType = "VARCHAR", charMaxlength = 128)
        private String title;
    }
}
