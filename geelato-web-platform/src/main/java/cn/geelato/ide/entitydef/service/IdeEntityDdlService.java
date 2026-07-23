package cn.geelato.ide.entitydef.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.ide.entitydef.dto.IdeEntityDefinition;
import cn.geelato.ide.entitydef.dto.IdeEntityFieldDefinition;
import cn.geelato.web.platform.srv.platform.service.BaseService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 简版 DDL 生成与执行服务（MySQL）。
 * <p>
 * 不依赖老 MetaDdlService / DbGenerateDynamicDao，完全自研，覆盖 MVP 场景：
 * <ul>
 *   <li>表不存在 → 生成 CREATE TABLE</li>
 *   <li>表存在 → 对比 information_schema，仅生成 ADD COLUMN（MVP 不支持 DROP/MODIFY，避免误删数据）</li>
 *   <li>按 connectId 切库执行</li>
 * </ul>
 *
 * @author geelato
 */
@Service
@Slf4j
public class IdeEntityDdlService {

    @Autowired
    private BaseService baseService;

    /**
     * 预览 DDL（不执行）。
     * 若表不存在，返回 CREATE TABLE；若存在，返回 ALTER TABLE ADD COLUMN（增量）。
     */
    public String previewDdl(IdeEntityDefinition def) {
        String connectId = def.getConnectId();
        String tableSchema = Strings.isBlank(def.getTableSchema()) ? currentSchema(connectId) : def.getTableSchema();
        boolean tableExists = isTableExists(def.getTableName(), connectId, tableSchema);
        if (!tableExists) {
            return generateCreateTable(def);
        }
        return generateAlterTableAddColumns(def, connectId, tableSchema);
    }

    /**
     * 执行 DDL（按 connectId 切库）。
     */
    public String executeDdl(IdeEntityDefinition def) {
        String sql = previewDdl(def);
        if (Strings.isBlank(sql)) {
            log.info("DDL 无需执行（已对齐）: {}", def.getEntityName());
            return "-- no-op (already aligned)";
        }
        JdbcTemplate jdbc = jdbcTemplateForConnectId(def.getConnectId());
        DynamicDataSourceHolder.setDataSourceKey(def.getConnectId());
        try {
            // SQL 可能含多条（ALTER ADD 多列），按分号切分逐条执行
            for (String stmt : splitSqlStatements(sql)) {
                if (Strings.isNotBlank(stmt)) {
                    jdbc.execute(stmt);
                    log.info("DDL 执行成功: {}", stmt);
                }
            }
            // 标记 platform_dev_table.synced = 1
            baseService.dao.getJdbcTemplate().update(
                    "UPDATE platform_dev_table SET synced = 1 WHERE entity_name = ? AND del_status = 0",
                    def.getEntityName());
        } finally {
            DynamicDataSourceHolder.clearDataSourceKey();
        }
        return sql;
    }

    // ======================================================================
    //                            DDL 生成
    // ======================================================================

    /**
     * 生成 CREATE TABLE 语句。
     */
    String generateCreateTable(IdeEntityDefinition def) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE `").append(def.getTableName()).append("` (\n");
        List<String> pkCols = new ArrayList<>();
        for (int i = 0; i < def.getColumns().size(); i++) {
            IdeEntityFieldDefinition f = def.getColumns().get(i);
            sb.append("  `").append(f.getColumnName()).append("` ")
              .append(toMysqlType(f));
            if (Boolean.FALSE.equals(f.getNullable())) {
                sb.append(" NOT NULL");
            }
            if (Boolean.TRUE.equals(f.getAutoIncrement())) {
                sb.append(" AUTO_INCREMENT");
            }
            if (Strings.isNotBlank(f.getDefaultValue())) {
                sb.append(" DEFAULT ").append(f.getDefaultValue());
            }
            if (Strings.isNotBlank(f.getComment())) {
                sb.append(" COMMENT '").append(escapeSql(f.getComment())).append("'");
            } else if (Strings.isNotBlank(f.getTitle())) {
                sb.append(" COMMENT '").append(escapeSql(f.getTitle())).append("'");
            }
            if (Boolean.TRUE.equals(f.getUnique())) {
                sb.append(",\n  UNIQUE KEY `uk_").append(f.getColumnName()).append("` (`").append(f.getColumnName()).append("`)");
            } else if (i < def.getColumns().size() - 1 || !pkCols.isEmpty() || Boolean.TRUE.equals(f.getUnique())) {
                // 主键或后续字段还有，补逗号
            }
            if (Boolean.TRUE.equals(f.getPrimaryKey())) {
                pkCols.add(f.getColumnName());
            }
            sb.append(",\n");
        }
        if (!pkCols.isEmpty()) {
            sb.append("  PRIMARY KEY (`").append(String.join("`,`", pkCols)).append("`)\n");
        } else {
            // 删除最后多余的逗号
            trimLastComma(sb);
        }
        sb.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        if (Strings.isNotBlank(def.getTitle())) {
            sb.append(" COMMENT='").append(escapeSql(def.getTitle())).append("'");
        }
        sb.append(";");
        return sb.toString();
    }

    /**
     * 生成 ALTER TABLE ADD COLUMN（增量）语句，不含已有列。
     */
    String generateAlterTableAddColumns(IdeEntityDefinition def, String connectId, String tableSchema) {
        // 查现有列
        JdbcTemplate jdbc = jdbcTemplateForConnectId(connectId);
        List<String> existingCols;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                    tableSchema, def.getTableName());
            existingCols = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                existingCols.add(String.valueOf(r.get("COLUMN_NAME")).toLowerCase());
            }
        } catch (Exception e) {
            log.warn("查询 information_schema 失败，schema={}, table={}", tableSchema, def.getTableName(), e);
            return "-- 查询现有列失败，请手动检查: " + e.getMessage();
        }
        // 找出新增列
        List<IdeEntityFieldDefinition> newCols = new ArrayList<>();
        for (IdeEntityFieldDefinition f : def.getColumns()) {
            if (!existingCols.contains(f.getColumnName().toLowerCase())) {
                newCols.add(f);
            }
        }
        if (newCols.isEmpty()) {
            return "-- no-op (all columns already exist)";
        }
        StringBuilder sb = new StringBuilder();
        for (IdeEntityFieldDefinition f : newCols) {
            sb.append("ALTER TABLE `").append(def.getTableName()).append("` ADD COLUMN `")
              .append(f.getColumnName()).append("` ").append(toMysqlType(f));
            if (Boolean.FALSE.equals(f.getNullable())) {
                sb.append(" NOT NULL");
            }
            if (Strings.isNotBlank(f.getDefaultValue())) {
                sb.append(" DEFAULT ").append(f.getDefaultValue());
            }
            if (Strings.isNotBlank(f.getComment())) {
                sb.append(" COMMENT '").append(escapeSql(f.getComment())).append("'");
            } else if (Strings.isNotBlank(f.getTitle())) {
                sb.append(" COMMENT '").append(escapeSql(f.getTitle())).append("'");
            }
            sb.append(";\n");
        }
        return sb.toString().trim();
    }

    /**
     * 字段定义 → MySQL 列类型字符串。
     */
    String toMysqlType(IdeEntityFieldDefinition f) {
        String t = f.getDataType();
        if (t == null) {
            return "VARCHAR(64)";
        }
        switch (t) {
            case "VARCHAR":
                return "VARCHAR(" + (f.getCharMaxLength() != null ? f.getCharMaxLength() : 64) + ")";
            case "DECIMAL":
                return "DECIMAL(" + (f.getNumericPrecision() != null ? f.getNumericPrecision() : 20) + ","
                        + (f.getNumericScale() != null ? f.getNumericScale() : 0) + ")";
            default:
                return t;
        }
    }

    // ======================================================================
    //                            helpers
    // ======================================================================

    /**
     * 按 connectId 取 JdbcTemplate。MVP 阶段：若 connectId 不在数据源注册表里，回退到 primary。
     */
    private JdbcTemplate jdbcTemplateForConnectId(String connectId) {
        if (Strings.isBlank(connectId)) {
            return baseService.dao.getJdbcTemplate();
        }
        try {
            // 优先复用平台已注册的 DynamicDataSource
            DataSource ds = baseService.dynamicDao.getJdbcTemplate().getDataSource();
            return new JdbcTemplate(ds);
        } catch (Exception e) {
            log.warn("取 connectId={} 的 DataSource 失败，回退 primary", connectId, e);
            return baseService.dao.getJdbcTemplate();
        }
    }

    private boolean isTableExists(String tableName, String connectId, String tableSchema) {
        JdbcTemplate jdbc = jdbcTemplateForConnectId(connectId);
        try {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                    Integer.class, tableSchema, tableName);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            log.warn("查询表是否存在失败: {}", tableName, e);
            return false;
        }
    }

    private String currentSchema(String connectId) {
        JdbcTemplate jdbc = jdbcTemplateForConnectId(connectId);
        try {
            return jdbc.queryForObject("SELECT DATABASE()", String.class);
        } catch (Exception e) {
            return "geelato";
        }
    }

    private List<String> splitSqlStatements(String sql) {
        List<String> stmts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                inString = !inString;
            }
            if (c == ';' && !inString) {
                String s = cur.toString().trim();
                if (!s.isEmpty() && !s.startsWith("--")) {
                    stmts.add(s);
                }
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        String last = cur.toString().trim();
        if (!last.isEmpty() && !last.startsWith("--")) {
            stmts.add(last);
        }
        return stmts;
    }

    private void trimLastComma(StringBuilder sb) {
        int len = sb.length();
        while (len > 0 && Character.isWhitespace(sb.charAt(len - 1))) {
            sb.deleteCharAt(len - 1);
            len--;
        }
        if (len > 0 && sb.charAt(len - 1) == ',') {
            sb.deleteCharAt(len - 1);
        }
    }

    private String escapeSql(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("'", "''");
    }
}
