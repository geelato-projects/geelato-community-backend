package cn.geelato.web.platform.m.model.service;

import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.constants.MetaDaoSql;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.meta.model.entity.TableForeign;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.schema.SchemaForeign;
import cn.geelato.core.util.SchemaUtils;
import cn.geelato.web.platform.m.base.rest.MetaDdlController;
import cn.geelato.web.platform.m.base.service.BaseSortableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Component
public class DevTableForeignService extends BaseSortableService {
    private static final Logger logger = LoggerFactory.getLogger(MetaDdlController.class);

    /**
     * 依据表格情况，从数据库中更新至 dev_column 中
     *
     * @param tableMeta
     * @param deleteAll
     */
    public void resetTableForeignByDataBase(TableMeta tableMeta, boolean deleteAll) {
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("mainTableSchema", null);
        queryParams.put("mainTableId", null);
        queryParams.put("mainTable", tableMeta.getEntityName());
        // dev_table
        List<TableForeign> tableForeigns = queryModel(TableForeign.class, queryParams);
        HashMap<String, TableForeign> tableForeignMap = new HashMap<>();
        if (tableForeigns != null && tableForeigns.size() > 0) {
            for (TableForeign meta : tableForeigns) {
                if (deleteAll) {
                    meta.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
                    isDeleteModel(meta);
                    continue;
                }
                if (Strings.isNotBlank(meta.getMainTableCol()) && !tableForeignMap.containsKey(meta.getMainTableCol())) {
                    tableForeignMap.put(meta.getMainTableCol(), meta);
                }
            }
        }
        if (!deleteAll) {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("schemaMethod", MetaDaoSql.TABLE_SCHEMA_METHOD);
            paramMap.put("tableName", tableMeta.getEntityName());
            // database_table_index
            List<Map<String, Object>> foreignList = dao.queryForMapList("queryTableForeignByDataBase", paramMap);
            List<SchemaForeign> schemaForeigns = SchemaUtils.buildData(SchemaForeign.class, foreignList);
            HashMap<String, SchemaForeign> schemaForeignMap = new HashMap<>();
            if (schemaForeigns != null && schemaForeigns.size() > 0) {
                for (SchemaForeign schema : schemaForeigns) {
                    if (Strings.isNotBlank(schema.getColumnName()) && !schemaForeignMap.containsKey(schema.getColumnName())) {
                        schemaForeignMap.put(schema.getColumnName(), schema);
                    }
                }
            }
            compareHashMapKeys(tableMeta, tableForeignMap, schemaForeignMap);
        }
    }

    /**
     * 比较 dev_column 和 数据库中字段 创建、更新、删除
     *
     * @param metaMap
     * @param schemaMap
     */
    private void compareHashMapKeys(TableMeta tableMeta, HashMap<String, TableForeign> metaMap, Map<String, SchemaForeign> schemaMap) {
        // 遍历 metaMap 的键 不存在：不处理
        // 遍历 schemaMap 的键 存在：更新；不存在：添加
        for (String key : schemaMap.keySet()) {
            SchemaForeign schema = schemaMap.get(key);
            TableForeign meta = schema.convertTableForeign(metaMap.get(key));
            meta.setAppId(tableMeta.getAppId());
            meta.setTenantCode(tableMeta.getTenantCode());
            meta.setMainTable(tableMeta.getTableName());
            meta.setMainTableId(tableMeta.getId());
            if (!metaMap.containsKey(key)) {
                createModel(meta);
            } else {
                updateModel(meta);
            }
        }
    }
}
