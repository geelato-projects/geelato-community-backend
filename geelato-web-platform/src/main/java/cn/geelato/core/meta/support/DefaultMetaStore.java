package cn.geelato.core.meta.support;

import cn.geelato.core.constants.MetaDaoSql;
import cn.geelato.core.meta.spi.MetaDefinitionBundle;
import cn.geelato.core.meta.spi.MetaStore;
import cn.geelato.core.orm.Dao;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 当前平台表结构的默认元数据实现。
 *
 * <p>本类位于业务层（geelato-web-platform），框架层（geelato-core）仅保留
 * {@link MetaStore} SPI 接口。保留原 package（cn.geelato.core.meta.support）
 * 以维持 import 一致性，由 {@code @ComponentScan(basePackages = {"cn.geelato"})} 发现。</p>
 */
@Component
public class DefaultMetaStore implements MetaStore {

    @Override
    public MetaDefinitionBundle load(Dao dao, Map<String, String> params) {
        String sql = MetaDaoSql.SQL_TABLE_LIST;
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (Strings.isNotBlank(entry.getValue())) {
                    sql = String.format("%s and find_in_set(%s, '%s')", sql, entry.getKey(), entry.getValue());
                }
            }
        }
        List<Map<String, Object>> tableList = dao.getJdbcTemplate().queryForList(sql);
        return new MetaDefinitionBundle(
                tableList,
                dao.getJdbcTemplate().queryForList(MetaDaoSql.SQL_COLUMN_LIST_BY_TABLE),
                dao.getJdbcTemplate().queryForList(MetaDaoSql.SQL_VIEW_LIST_BY_TABLE),
                dao.getJdbcTemplate().queryForList(MetaDaoSql.SQL_CHECK_LIST_BY_TABLE),
                dao.getJdbcTemplate().queryForList(MetaDaoSql.SQL_FOREIGN_LIST_BY_TABLE)
        );
    }

    @Override
    public MetaDefinitionBundle loadByEntityName(Dao dao, String entityName) {
        String tableListSql = MetaDaoSql.SQL_TABLE_LIST;
        if (StringUtils.isNotBlank(entityName)) {
            tableListSql = String.format(MetaDaoSql.SQL_TABLE_LIST + " and entity_name='%s'", entityName);
        }
        List<Map<String, Object>> tableList = dao.getJdbcTemplate().queryForList(tableListSql);
        if (tableList.isEmpty()) {
            return new MetaDefinitionBundle(tableList, List.of(), List.of(), List.of(), List.of());
        }
        Map<String, Object> table = tableList.get(0);
        Object tableId = table.get("id");
        Object tableEntityName = table.get("entity_name");
        Object connectId = table.get("connect_id");
        return new MetaDefinitionBundle(
                tableList,
                dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_COLUMN_LIST_BY_TABLE + " and table_id='%s'", tableId)),
                dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_VIEW_LIST_BY_TABLE + " and entity_name='%s' and connect_id='%s'", tableEntityName, connectId)),
                dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_CHECK_LIST_BY_TABLE + " and table_id='%s'", tableId)),
                dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_FOREIGN_LIST_BY_TABLE + " and main_table='%s'", table.get("table_name")))
        );
    }

    @Override
    public MetaDefinitionBundle loadByViewName(Dao dao, String viewName) {
        String viewListSql = MetaDaoSql.SQL_VIEW_LIST_BY_TABLE;
        if (Strings.isNotEmpty(viewName)) {
            viewListSql = String.format(MetaDaoSql.SQL_VIEW_LIST_BY_TABLE + " and view_name='%s'", viewName);
        }
        return new MetaDefinitionBundle(
                List.of(),
                List.of(),
                dao.getJdbcTemplate().queryForList(viewListSql),
                List.of(),
                List.of()
        );
    }
}
