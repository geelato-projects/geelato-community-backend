package cn.geelato.web.platform.m.model.service;

import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.meta.model.entity.TableForeign;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.schema.SchemaForeign;
import cn.geelato.web.platform.m.base.service.DbGenerateDynamicDao;
import cn.geelato.web.platform.m.base.service.BaseSortableService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Component
@Slf4j
public class DevTableForeignService extends BaseSortableService {
    @Lazy
    @Autowired
    protected DbGenerateDynamicDao dbGenerateDao;

    /**
     * 依据表格情况，从数据库中更新至 dev_column 中
     * <p>
     * 根据表格元数据对象，从数据库中查询外键信息，并根据是否需要删除所有现有外键来更新dev_column表中外键的记录。
     *
     * @param tableMeta 表格元数据对象，包含表格的基本信息
     * @param deleteAll 是否删除所有现有外键
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
                    meta.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
                    isDeleteModel(meta);
                    continue;
                }
                if (Strings.isNotBlank(meta.getMainTableCol()) && !tableForeignMap.containsKey(meta.getMainTableCol())) {
                    tableForeignMap.put(meta.getMainTableCol(), meta);
                }
            }
        }
        if (!deleteAll) {
            // database_table_index
            switchDbByConnectId(tableMeta.getConnectId());
            List<SchemaForeign> schemaForeigns = dbGenerateDao.dbQueryForeignsByTableName(tableMeta.getDbType(), tableMeta.getEntityName());
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
     * 比较 dev_column 和 数据库中外键 创建、更新、删除
     * <p>
     * 根据传入的 dev_column 外键映射（metaMap）和数据库外键映射（schemaMap），对数据库中的外键记录进行相应的创建、更新或删除操作。
     *
     * @param tableMeta 表元数据对象，包含表的基本信息
     * @param metaMap   dev_column外键映射，键为外键名，值为对应的TableForeign对象
     * @param schemaMap 数据库外键映射，键为外键名，值为对应的SchemaForeign对象
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
