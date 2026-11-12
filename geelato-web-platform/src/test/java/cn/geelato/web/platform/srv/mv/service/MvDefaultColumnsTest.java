package cn.geelato.web.platform.srv.mv.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台默认字段强制校验的纯逻辑部分：必含字段集来源于 column_default.json（经 MetaManager 加载），
 * 缺失计算大小写不敏感。
 */
class MvDefaultColumnsTest {

    @Test
    @DisplayName("必含字段集来自 column_default.json，共 12 项且含关键字段")
    void requiredColumnNamesFromColumnDefaultJson() {
        Set<String> required = MaterializedViewService.requiredColumnNames();
        assertEquals(12, required.size());
        assertTrue(required.containsAll(Set.of(
                "id", "dept_id", "bu_id", "tenant_code", "del_status",
                "update_at", "updater", "updater_name", "create_at", "creator",
                "creator_name", "delete_at")));
    }

    @Test
    @DisplayName("实际列包含全部默认字段（含大小写混用）时缺失为空")
    void missingIsEmptyWhenAllPresent() {
        List<String> actual = List.of("ID", "Dept_Id", "BU_ID", "TENANT_CODE", "DEL_STATUS",
                "UPDATE_AT", "UPDATER", "UPDATER_NAME", "CREATE_AT", "CREATOR", "CREATOR_NAME", "DELETE_AT", "extra");
        assertTrue(MaterializedViewService.missingRequiredColumns(actual).isEmpty());
    }

    @Test
    @DisplayName("缺 tenant_code 与 creator 时正确报缺")
    void missingReportsAbsentColumns() {
        List<String> actual = List.of("id", "dept_id", "bu_id", "del_status",
                "update_at", "updater", "updater_name", "create_at", "creator_name", "delete_at");
        assertEquals(List.of("creator", "tenant_code"), MaterializedViewService.missingRequiredColumns(actual));
    }

    @Test
    @DisplayName("空列清单报缺全部必含字段")
    void missingAllWhenActualEmpty() {
        assertEquals(MaterializedViewService.requiredColumnNames().size(),
                MaterializedViewService.missingRequiredColumns(List.of()).size());
        assertEquals(MaterializedViewService.requiredColumnNames().size(),
                MaterializedViewService.missingRequiredColumns(null).size());
    }
}
