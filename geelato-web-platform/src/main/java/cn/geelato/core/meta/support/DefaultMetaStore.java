package cn.geelato.core.meta.support;

import cn.geelato.core.constants.MetaDaoSql;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.TableCheck;
import cn.geelato.core.meta.model.entity.TableForeign;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.core.meta.spi.MetaDefinitionBundle;
import cn.geelato.core.meta.spi.MetaStore;
import cn.geelato.core.orm.Dao;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 当前平台表结构的默认元数据实现。
 *
 * <p>本类位于业务层（geelato-web-platform），框架层（geelato-core）仅保留
 * {@link MetaStore} SPI 接口。保留原 package（cn.geelato.core.meta.support）
 * 以维持 import 一致性，由 {@code @ComponentScan(basePackages = {"cn.geelato"})} 发现。</p>
 *
 * <p>本实现从平台元数据表读取定义，因此自行通过构造器注入 {@link Dao}，
 * 而非由 {@code MetaManager} 通过方法参数透传。</p>
 */
@Component
public class DefaultMetaStore implements MetaStore {

    private final Dao dao;

    public DefaultMetaStore(@Qualifier("primaryDao") Dao dao) {
        this.dao = dao;
    }

    @Override
    public MetaDefinitionBundle load(Map<String, String> params) {
        String sql = MetaDaoSql.SQL_TABLE_LIST;
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (Strings.isNotBlank(entry.getValue())) {
                    sql = String.format("%s and find_in_set(%s, '%s')", sql, entry.getKey(), entry.getValue());
                }
            }
        }
        final String tableListSql = sql;
        // 5 条元数据查询互相独立，并行执行以缩短加载耗时（JdbcTemplate 与 HikariCP 均线程安全）。
        ExecutorService pool = Executors.newFixedThreadPool(5, r -> {
            Thread t = new Thread(r, "meta-store-load");
            t.setDaemon(true);
            return t;
        });
        try {
            CompletableFuture<List<TableMeta>> tables = CompletableFuture.supplyAsync(
                    () -> toTableList(dao.getJdbcTemplate().queryForList(tableListSql)), pool);
            CompletableFuture<List<ColumnMeta>> columns = CompletableFuture.supplyAsync(
                    () -> toColumnList(dao.getJdbcTemplate().queryForList(MetaDaoSql.SQL_COLUMN_LIST_BY_TABLE)), pool);
            CompletableFuture<List<TableView>> views = CompletableFuture.supplyAsync(
                    () -> toViewList(dao.getJdbcTemplate().queryForList(MetaDaoSql.SQL_VIEW_LIST_BY_TABLE)), pool);
            CompletableFuture<List<TableCheck>> checks = CompletableFuture.supplyAsync(
                    () -> toCheckList(dao.getJdbcTemplate().queryForList(MetaDaoSql.SQL_CHECK_LIST_BY_TABLE)), pool);
            CompletableFuture<List<TableForeign>> foreigns = CompletableFuture.supplyAsync(
                    () -> toForeignList(dao.getJdbcTemplate().queryForList(MetaDaoSql.SQL_FOREIGN_LIST_BY_TABLE)), pool);
            CompletableFuture.allOf(tables, columns, views, checks, foreigns).join();
            return new MetaDefinitionBundle(
                    tables.join(), columns.join(), views.join(), checks.join(), foreigns.join());
        } finally {
            pool.shutdownNow();
        }
    }

    @Override
    public MetaDefinitionBundle loadByEntityName(String entityName) {
        String tableListSql = MetaDaoSql.SQL_TABLE_LIST;
        if (StringUtils.isNotBlank(entityName)) {
            tableListSql = String.format(MetaDaoSql.SQL_TABLE_LIST + " and entity_name='%s'", entityName);
        }
        List<Map<String, Object>> tableRows = dao.getJdbcTemplate().queryForList(tableListSql);
        if (tableRows.isEmpty()) {
            return new MetaDefinitionBundle(List.of(), List.of(), List.of(), List.of(), List.of());
        }
        Map<String, Object> table = tableRows.get(0);
        Object tableId = table.get("id");
        Object tableEntityName = table.get("entity_name");
        Object connectId = table.get("connect_id");
        return new MetaDefinitionBundle(
                toTableList(tableRows),
                toColumnList(dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_COLUMN_LIST_BY_TABLE + " and table_id='%s'", tableId))),
                toViewList(dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_VIEW_LIST_BY_TABLE + " and entity_name='%s' and connect_id='%s'", tableEntityName, connectId))),
                toCheckList(dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_CHECK_LIST_BY_TABLE + " and table_id='%s'", tableId))),
                toForeignList(dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_FOREIGN_LIST_BY_TABLE + " and main_table='%s'", table.get("table_name"))))
        );
    }

    @Override
    public MetaDefinitionBundle loadByViewName(String viewName) {
        String viewListSql = MetaDaoSql.SQL_VIEW_LIST_BY_TABLE;
        if (Strings.isNotEmpty(viewName)) {
            viewListSql = String.format(MetaDaoSql.SQL_VIEW_LIST_BY_TABLE + " and view_name='%s'", viewName);
        }
        return new MetaDefinitionBundle(
                List.of(),
                List.of(),
                toViewList(dao.getJdbcTemplate().queryForList(viewListSql)),
                List.of(),
                List.of()
        );
    }

    /** 将平台表行 Map 列表转为强类型 {@link TableMeta} 列表。 */
    private static List<TableMeta> toTableList(List<Map<String, Object>> rows) {
        return rows.stream().map(TableMeta::new).collect(Collectors.toList());
    }

    /** 将平台列行 Map 列表转为强类型 {@link ColumnMeta} 列表。 */
    private static List<ColumnMeta> toColumnList(List<Map<String, Object>> rows) {
        return rows.stream().map(ColumnMeta::new).collect(Collectors.toList());
    }

    /** 将平台视图行 Map 列表转为强类型 {@link TableView} 列表。 */
    private static List<TableView> toViewList(List<Map<String, Object>> rows) {
        return rows.stream().map(TableView::new).collect(Collectors.toList());
    }

    /** 将表检查行 Map 列表转为强类型 {@link TableCheck} 列表。 */
    private static List<TableCheck> toCheckList(List<Map<String, Object>> rows) {
        return rows.stream().map(TableCheck::new).collect(Collectors.toList());
    }

    /** 将表外键行 Map 列表转为强类型 {@link TableForeign} 列表。 */
    private static List<TableForeign> toForeignList(List<Map<String, Object>> rows) {
        return rows.stream().map(TableForeign::new).collect(Collectors.toList());
    }
}
