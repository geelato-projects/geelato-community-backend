package cn.geelato.datasource;

import cn.geelato.core.enums.TableTypeEnum;
import cn.geelato.core.enums.ViewTypeEnum;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Id;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterializedViewGuardTest {

    private static final String MV_ENTITY = "mv_guard_materialized_entity";
    private static final String NORMAL_ENTITY = "mv_guard_normal_entity";
    private static final String MV_VIEW_ENTITY = "mv_guard_materialized_view";

    @BeforeAll
    static void registerEntities() {
        MetaManager manager = MetaManager.singleInstance();
        manager.parseOne(MaterializedTestEntity.class);
        manager.parseOne(NormalTestEntity.class);
        // 在线物化视图实体（表行登记形态）：类注册后以 table_type=materialized 的 TableMeta 覆盖绑定
        TableMeta tableMeta = new TableMeta("mv_guard_mv", "物化视图门禁测试", MV_ENTITY, null);
        tableMeta.setTableType(TableTypeEnum.MATERIALIZED.getValue());
        manager.parseTableEntity(tableMeta, List.of(), List.of(), List.of(), List.of());
        // 在线物化视图实体（视图行登记形态）：view_type=materialized，实体名即视图名
        TableView tableView = new TableView();
        tableView.setViewName(MV_VIEW_ENTITY);
        tableView.setViewType(ViewTypeEnum.MATERIALIZED.getCode());
        manager.parseViewEntity(tableView);
    }

    @AfterAll
    static void unregisterEntities() {
        MetaManager manager = MetaManager.singleInstance();
        manager.removeOne(MV_ENTITY);
        manager.removeOne(NORMAL_ENTITY);
        manager.removeOne(MV_VIEW_ENTITY);
    }

    @Test
    void writeMethodOnMaterializedEntityThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> MaterializedViewGuard.assertWritable("save", MV_ENTITY));
        assertTrue(ex.getMessage().contains(MV_ENTITY));
        assertTrue(ex.getMessage().contains("只读"));
    }

    @Test
    void eachWriteMethodBlocked() {
        for (String method : new String[]{"save", "insert", "update", "delete", "multiSave",
                "multiDelete", "batchSave", "executeUpdate", "nativeExecute", "execute", "executeKey", "executeForNull"}) {
            assertThrows(IllegalStateException.class,
                    () -> MaterializedViewGuard.assertWritable(method, MV_ENTITY), method);
        }
    }

    @Test
    void writeMethodOnViewRowMaterializedEntityThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> MaterializedViewGuard.assertWritable("insert", MV_VIEW_ENTITY));
        assertTrue(ex.getMessage().contains(MV_VIEW_ENTITY));
    }

    @Test
    void readMethodOnMaterializedEntityPasses() {
        assertDoesNotThrow(() -> MaterializedViewGuard.assertWritable("queryForMapList", MV_ENTITY));
        assertDoesNotThrow(() -> MaterializedViewGuard.assertWritable("queryList", MV_ENTITY));
    }

    @Test
    void writeMethodOnNormalEntityPasses() {
        assertDoesNotThrow(() -> MaterializedViewGuard.assertWritable("save", NORMAL_ENTITY));
    }

    @Test
    void writeMethodOnUnknownOrNullEntityPasses() {
        assertDoesNotThrow(() -> MaterializedViewGuard.assertWritable("save", "mv_guard_not_registered_entity"));
        assertDoesNotThrow(() -> MaterializedViewGuard.assertWritable("save", null));
    }

    @Entity(name = MV_ENTITY)
    public static class MaterializedTestEntity {
        @Id
        private String id;
    }

    @Entity(name = NORMAL_ENTITY)
    public static class NormalTestEntity {
        @Id
        private String id;
    }
}
