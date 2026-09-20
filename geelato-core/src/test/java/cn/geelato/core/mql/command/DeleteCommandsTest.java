package cn.geelato.core.mql.command;

import cn.geelato.core.GlobalContext;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.DeleteMode;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Id;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 删除模式解析矩阵：调用级显式 ＞ 实体级 @Entity(deleteMode) ＞ 全局默认（GlobalContext 固化常量，恒 LOGIC）。
 * 逻辑删除无 delStatus 字段属配置错误，硬失败并给出可操作提示。
 */
class DeleteCommandsTest {

    private static final MetaManager metaManager = MetaManager.singleInstance();

    @BeforeAll
    static void setUp() {
        metaManager.parseOne(DelCmdUserEntity.class);
        metaManager.parseOne(DelCmdPlainEntity.class);
        metaManager.parseOne(DelCmdPhyEntity.class);
        metaManager.parseOne(DelCmdLogicEntity.class);
    }

    @AfterAll
    static void tearDown() {
        metaManager.removeOne("DelCmdUser");
        metaManager.removeOne("DelCmdPlain");
        metaManager.removeOne("DelCmdPhy");
        metaManager.removeOne("DelCmdLogic");
    }

    @Test
    void autoWithLogicFieldFollowsGlobalLogic() {
        // 全局默认为代码固化的 LOGIC，AUTO 实体落点即逻辑删除
        assertEquals(DeleteMode.LOGIC, GlobalContext.getDefaultDeleteMode());
        assertEquals(DeleteMode.LOGIC, DeleteCommands.resolve(em("DelCmdUser"), false));
    }

    @Test
    void physicalRequestedBeatsEntityAndGlobal() {
        // 调用级显式压过实体级 LOGIC 显式与全局 LOGIC
        assertEquals(DeleteMode.PHYSICAL, DeleteCommands.resolve(em("DelCmdLogic"), true));
        assertEquals(DeleteMode.PHYSICAL, DeleteCommands.resolve(em("DelCmdUser"), true));
    }

    @Test
    void entityExplicitPhysicalBeatsGlobalLogic() {
        // 实体显式 PHYSICAL，无视全局 LOGIC（实体有 delStatus 字段也走物理）
        assertEquals(DeleteMode.PHYSICAL, DeleteCommands.resolve(em("DelCmdPhy"), false));
        // 实体显式 LOGIC，与全局默认一致
        assertEquals(DeleteMode.LOGIC, DeleteCommands.resolve(em("DelCmdLogic"), false));
    }

    @Test
    void autoWithoutDelStatusFieldFollowsGlobalLogicAndHardFails() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DeleteCommands.resolve(em("DelCmdPlain"), false));
        assertTrue(ex.getMessage().contains("DelCmdPlain"));
        assertTrue(ex.getMessage().contains(ColumnDefault.DEL_STATUS_FIELD));
    }

    @Test
    void fillLogicDeleteValuesCoversExistingFieldsOnly() {
        DeleteCommand command = new DeleteCommand();
        DeleteCommands.fillLogicDeleteValues(command, em("DelCmdUser"));
        assertEquals(1, command.getValueMap().get(ColumnDefault.DEL_STATUS_FIELD));
        assertTrue(command.getValueMap().containsKey(ColumnDefault.DELETE_AT_FIELD));
        assertTrue(command.getValueMap().containsKey(ColumnDefault.UPDATE_AT_FIELD));
        assertTrue(command.getValueMap().containsKey(ColumnDefault.UPDATER_FIELD));
        assertTrue(command.getValueMap().containsKey(ColumnDefault.UPDATER_NAME_FIELD));
        // fields 与 valueMap 同步
        assertEquals(command.getValueMap().keySet().size(), command.getFields().length);

        // 无 delStatus 的精简实体（仅 id/title）无可填充字段，valueMap 为空
        DeleteCommand plain = new DeleteCommand();
        DeleteCommands.fillLogicDeleteValues(plain, em("DelCmdPlain"));
        assertTrue(plain.getValueMap().isEmpty());
    }

    private EntityMeta em(String entityName) {
        return metaManager.getByEntityName(entityName);
    }

    @Getter
    @Setter
    @Entity(name = "DelCmdUser", table = "t_del_cmd_user")
    public static class DelCmdUserEntity extends BaseEntity {
        @Col(name = "title", dataType = "VARCHAR", charMaxlength = 128)
        private String title;
    }

    @Getter
    @Setter
    @Entity(name = "DelCmdPlain", table = "t_del_cmd_plain")
    public static class DelCmdPlainEntity {
        @Id
        @Col(name = "id", dataType = "VARCHAR", charMaxlength = 32)
        private String id;

        @Col(name = "title", dataType = "VARCHAR", charMaxlength = 128)
        private String title;
    }

    @Getter
    @Setter
    @Entity(name = "DelCmdPhy", table = "t_del_cmd_phy", deleteMode = DeleteMode.PHYSICAL)
    public static class DelCmdPhyEntity extends BaseEntity {
        @Col(name = "title", dataType = "VARCHAR", charMaxlength = 128)
        private String title;
    }

    @Getter
    @Setter
    @Entity(name = "DelCmdLogic", table = "t_del_cmd_logic", deleteMode = DeleteMode.LOGIC)
    public static class DelCmdLogicEntity extends BaseEntity {
        @Col(name = "title", dataType = "VARCHAR", charMaxlength = 128)
        private String title;
    }
}
