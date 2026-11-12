package cn.geelato.core.meta;

import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.view.TableView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 视图实体 dbType：视图有自身连接（如物化副本行 connect_id=引擎连接）时不继承主体 dbType，
 * 方言由 resolveEffectiveDbType 按执行连接解析；无自身连接的老数据视图保留继承兜底。
 */
class MetaReflexViewDbTypeTest {

    private static final String SUBJECT = "mv_dbtype_subject_entity";

    private final MetaManager metaManager = MetaManager.singleInstance();

    @AfterEach
    void cleanUp() {
        metaManager.removeOne(SUBJECT);
        metaManager.removeOne("mv_view_with_connect");
        metaManager.removeOne("mv_view_without_connect");
    }

    @Test
    void viewWithOwnConnectIdDoesNotInheritSubjectDbType() {
        registerSubjectEntity("mysql");

        TableView view = baseView("mv_view_with_connect");
        view.setConnectId("engine_connect");

        EntityMeta em = MetaReflex.getEntityMetaByView(view);
        assertNull(em.getTableMeta().getDbType());
        assertEquals("engine_connect", em.getTableMeta().getConnectId());
    }

    @Test
    void viewWithoutConnectIdInheritsSubjectDbType() {
        registerSubjectEntity("mysql");

        TableView view = baseView("mv_view_without_connect");

        assertEquals("mysql", MetaReflex.getEntityMetaByView(view).getTableMeta().getDbType());
    }

    private void registerSubjectEntity(String dbType) {
        TableMeta tableMeta = new TableMeta();
        tableMeta.setEntityName(SUBJECT);
        tableMeta.setTableName("mv_dbtype_subject_table");
        tableMeta.setDbType(dbType);
        metaManager.parseTableEntity(tableMeta, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private TableView baseView(String viewName) {
        TableView view = new TableView();
        view.setViewName(viewName);
        view.setEntityName(SUBJECT);
        view.setTitle(viewName);
        return view;
    }
}
