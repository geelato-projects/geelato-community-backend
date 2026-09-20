package cn.geelato.archive.engine.channel;

import cn.geelato.archive.entity.ArchivePolicy;
import cn.geelato.archive.enums.TargetTypeEnum;
import cn.geelato.archive.exception.ArchiveException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MySQL 归档通道（一期唯一目标类型），三种执行模式：
 * <ul>
 *   <li>SERVER：同数据源场景，INSERT INTO 目标 SELECT … WHERE id IN 服务端完成，数据零出库；</li>
 *   <li>LOCAL_FILE：跨实例，流式导出 TSV 临时文件 → LOAD DATA LOCAL INFILE（MySQL 最快导入协议；
 *       localInfile 被禁或含二进制列时自动降级 JDBC）；</li>
 *   <li>JDBC：拉行-推行批量管道（rewriteBatchedStatements 由归档连接参数开启），通用兜底。</li>
 * </ul>
 * <p>所有模式共用同一套对账：清残留 → 写入 → 载入数与目标 count 双重复核，
 * 任一不符抛 70003（源库未动，零丢失）。</p>
 */
@Component
@Slf4j
public class MySqlArchiveChannel implements ArchiveChannel {

    private static final int IN_CLAUSE_CHUNK = 500;

    private final JdbcTemplate sourceJdbc;
    private final ArchiveTargetDataSourceResolver resolver;
    /** 目标表 → 是否含二进制列（LOCAL_FILE 不支持，降级 JDBC） */
    private final Map<String, Boolean> binaryColumnCache = new ConcurrentHashMap<>();

    @Autowired
    public MySqlArchiveChannel(@Qualifier("primaryDao") cn.geelato.core.orm.Dao primaryDao,
                               ArchiveTargetDataSourceResolver resolver) {
        this.sourceJdbc = primaryDao.getJdbcTemplate();
        this.resolver = resolver;
    }

    @Override
    public TargetTypeEnum supports() {
        return TargetTypeEnum.MYSQL;
    }

    // ==================== 结构核对 ====================

    @Override
    public List<String> ensureTargetTable(ArchivePolicy policy, String resolvedTarget) {
        String source = policy.getTableName();
        List<String> sourceColumns = readColumnNames(source);
        if (sourceColumns.isEmpty()) {
            throw ArchiveException.validationError("源表 " + source + " 无列信息（表不存在或无权限读取元数据）");
        }
        binaryColumnCache.put(cacheKey(policy, resolvedTarget), hasBinaryColumn(source));

        JdbcTemplate targetJdbc = targetJdbc(policy);
        if (!tableExists(targetJdbc, resolvedTarget)) {
            createTargetTableLikeSource(sourceJdbc, targetJdbc, source, resolvedTarget);
            log.info("已按源表 {} 自动创建归档目标表 {}", source, resolvedTarget);
            return sourceColumns;
        }
        List<String> targetColumns = readColumnNames(targetJdbc, resolvedTarget);
        List<String> missing = diff(sourceColumns, targetColumns);
        if (!missing.isEmpty()) {
            throw ArchiveException.schemaDrift("归档目标表 " + resolvedTarget + " 列结构漂移：源表存在而目标缺少列 "
                    + missing + "（禁止静默跳列，请先对齐结构或重建目标表）");
        }
        List<String> extra = diff(targetColumns, sourceColumns);
        if (!extra.isEmpty()) {
            log.warn("归档目标表 {} 存在源表没有的额外列 {}（不参与搬移）", resolvedTarget, extra);
        }
        return sourceColumns;
    }

    // ==================== 搬移（阶段1） ====================

    @Override
    public long migrateBatch(ArchivePolicy policy, String resolvedTarget, List<String> sourceColumns,
                             List<String> ids, String mode, Path batchDir) {
        JdbcTemplate targetJdbc = targetJdbc(policy);
        String source = policy.getTableName();
        boolean hasBinary = binaryColumnCache.getOrDefault(cacheKey(policy, resolvedTarget), Boolean.FALSE);

        long loaded;
        if ("SERVER".equals(mode)) {
            loaded = migrateByServerSideInsert(sourceJdbc, source, resolvedTarget, sourceColumns, ids);
        } else if ("LOCAL_FILE".equals(mode) && !hasBinary) {
            try {
                loaded = migrateByLocalFile(sourceJdbc, targetJdbc, source, resolvedTarget, sourceColumns, ids, batchDir);
            } catch (Exception e) {
                if (isLocalInfileDisabled(e)) {
                    log.warn("归档库连接不允许 LOAD DATA LOCAL INFILE（{}），本批降级为 JDBC 管道", e.getMessage());
                    loaded = migrateByJdbcPipe(sourceJdbc, targetJdbc, source, resolvedTarget, sourceColumns, ids);
                } else {
                    throw e;
                }
            }
        } else {
            if ("LOCAL_FILE".equals(mode) && hasBinary) {
                log.info("源表 {} 含二进制列，LOCAL_FILE 模式降级为 JDBC 管道", source);
            }
            loaded = migrateByJdbcPipe(sourceJdbc, targetJdbc, source, resolvedTarget, sourceColumns, ids);
        }

        // 对账双重复核：载入行数 与 目标 count
        long targetCount = countByIds(resolvedTarget, ids, targetJdbc);
        if (loaded != ids.size() || targetCount != ids.size()) {
            throw ArchiveException.targetWriteMismatch(String.format(
                    "归档库写入对账不一致：表 %s → %s，本批期望 %d 行，载入 %d 行，目标复核 %d 行（源库未动，零丢失；"
                            + "修复后重跑安全，残留将被批首清理）", source, resolvedTarget, ids.size(), loaded, targetCount));
        }
        return loaded;
    }

    /** SERVER 模式：INSERT INTO 目标 SELECT（同数据源，服务端完成） */
    private long migrateByServerSideInsert(JdbcTemplate jdbc, String source, String target,
                                           List<String> columns, List<String> ids) {
        String columnList = String.join(", ", columns);
        long total = 0;
        for (List<String> chunk : partition(ids, IN_CLAUSE_CHUNK)) {
            String placeholders = joinPlaceholders(chunk.size());
            String sql = String.format("INSERT INTO %s (%s) SELECT %s FROM %s WHERE id IN (%s)",
                    target, columnList, columnList, source, placeholders);
            total += jdbc.update(sql, chunk.toArray());
        }
        return total;
    }

    /** LOCAL_FILE 模式：源库流式导出 TSV → 归档库 LOAD DATA LOCAL INFILE */
    private long migrateByLocalFile(JdbcTemplate sourceJdbc, JdbcTemplate targetJdbc, String source, String target,
                                    List<String> columns, List<String> ids, Path batchDir) {
        Path file = batchDir.resolve("batch-" + System.nanoTime() + ".tsv");
        try {
            Files.createDirectories(batchDir);
            exportTsv(sourceJdbc, source, columns, ids, file);
            return loadLocalInfile(targetJdbc, target, columns, file);
        } catch (IOException | java.io.UncheckedIOException e) {
            throw new IllegalStateException("LOCAL_FILE 模式临时文件读写失败：" + file, e);
        } finally {
            deleteQuietly(file);
        }
    }

    /** JDBC 模式：源库拉行 → 归档库批量插入（原生 setObject 保留类型） */
    private long migrateByJdbcPipe(JdbcTemplate sourceJdbc, JdbcTemplate targetJdbc, String source, String target,
                                   List<String> columns, List<String> ids) {
        String columnList = String.join(", ", columns);
        String placeholders = String.join(", ", java.util.Collections.nCopies(columns.size(), "?"));
        String insertSql = String.format("INSERT INTO %s (%s) VALUES (%s)", target, columnList, placeholders);
        long total = 0;
        for (List<String> chunk : partition(ids, IN_CLAUSE_CHUNK)) {
            String selectSql = String.format("SELECT %s FROM %s WHERE id IN (%s)",
                    columnList, source, joinPlaceholders(chunk.size()));
            List<Object[]> rows = sourceJdbc.query(selectSql, chunk.toArray(),
                    (ResultSetExtractor<List<Object[]>>) rs -> {
                        int n = rs.getMetaData().getColumnCount();
                        List<Object[]> list = new ArrayList<>();
                        while (rs.next()) {
                            Object[] row = new Object[n];
                            for (int i = 0; i < n; i++) {
                                row[i] = rs.getObject(i + 1);
                            }
                            list.add(row);
                        }
                        return list;
                    });
            if (!rows.isEmpty()) {
                targetJdbc.batchUpdate(insertSql, rows);
            }
            total += rows.size();
        }
        return total;
    }

    // ==================== 对账与查询 ====================

    @Override
    public long countByIds(String resolvedTarget, List<String> ids) {
        return countByIds(resolvedTarget, ids, null);
    }

    private long countByIds(String resolvedTarget, List<String> ids, JdbcTemplate jdbc) {
        JdbcTemplate template = jdbc != null ? jdbc : sourceJdbc;
        long total = 0;
        for (List<String> chunk : partition(ids, IN_CLAUSE_CHUNK)) {
            String sql = String.format("SELECT COUNT(*) FROM %s WHERE id IN (%s)",
                    resolvedTarget, joinPlaceholders(chunk.size()));
            Long count = template.queryForObject(sql, chunk.toArray(), Long.class);
            if (count != null) {
                total += count;
            }
        }
        return total;
    }

    @Override
    public Map<String, Object> queryArchived(ArchivePolicy policy, String resolvedTarget, Map<String, Object> filters,
                                             int pageNum, int pageSize) {
        JdbcTemplate targetJdbc = targetJdbc(policy);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        Object id = filters == null ? null : filters.get("id");
        if (id instanceof String s && !s.isBlank()) {
            where.append(" AND id = ?");
            params.add(s);
        }
        String countSql = "SELECT COUNT(*) FROM " + resolvedTarget + where;
        Long total = targetJdbc.queryForObject(countSql, params.toArray(), Long.class);
        String dataSql = "SELECT * FROM " + resolvedTarget + where + " ORDER BY id ASC LIMIT ? OFFSET ?";
        params.add(pageSize);
        params.add(Math.max(0, (pageNum - 1) * pageSize));
        List<Map<String, Object>> items = targetJdbc.queryForList(dataSql, params.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total == null ? 0 : total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        result.put("items", items);
        return result;
    }

    /** 回迁阶段的目标清理：DELETE FROM target WHERE id IN（源库写入成功后调用） */
    public long deleteTargetByIds(ArchivePolicy policy, String resolvedTarget, List<String> ids) {
        JdbcTemplate targetJdbc = targetJdbc(policy);
        long total = 0;
        for (List<String> chunk : partition(ids, IN_CLAUSE_CHUNK)) {
            String sql = String.format("DELETE FROM %s WHERE id IN (%s)",
                    resolvedTarget, joinPlaceholders(chunk.size()));
            total += targetJdbc.update(sql, chunk.toArray());
        }
        return total;
    }

    // ==================== TSV 导出 / LOAD DATA ====================

    private void exportTsv(JdbcTemplate sourceJdbc, String source, List<String> columns, List<String> ids, Path file) throws IOException {
        String columnList = String.join(", ", columns);
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (List<String> chunk : partition(ids, IN_CLAUSE_CHUNK)) {
                String selectSql = String.format("SELECT %s FROM %s WHERE id IN (%s)",
                        columnList, source, joinPlaceholders(chunk.size()));
                sourceJdbc.query(selectSql, chunk.toArray(), (ResultSetExtractor<Void>) rs -> {
                    int n = rs.getMetaData().getColumnCount();
                    StringBuilder sb = new StringBuilder();
                    while (rs.next()) {
                        sb.setLength(0);
                        for (int i = 1; i <= n; i++) {
                            if (i > 1) {
                                sb.append('\t');
                            }
                            appendTsvValue(sb, rs.getObject(i));
                        }
                        sb.append('\n');
                        try {
                            writer.write(sb.toString());
                        } catch (IOException ioe) {
                            throw new java.io.UncheckedIOException(ioe);
                        }
                    }
                    return null;
                });
            }
        }
    }

    private void appendTsvValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("\\N");
            return;
        }
        String s;
        if (value instanceof Timestamp t) {
            s = t.toString();
        } else if (value instanceof java.sql.Date || value instanceof java.sql.Time) {
            s = value.toString();
        } else if (value instanceof byte[]) {
            // 二进制列应已在 migrateBatch 入口被检测降级为 JDBC，到这里属编程错误，硬失败
            throw new IllegalStateException("LOCAL_FILE 模式不支持二进制列（应已降级 JDBC）");
        } else {
            s = value.toString();
        }
        sb.append(s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r"));
    }

    private long loadLocalInfile(JdbcTemplate targetJdbc, String target, List<String> columns, Path file) {
        String columnList = String.join(", ", columns);
        // ESCAPED BY '\\'：TSV 转义约定（\N=NULL、\\、\t、\n、\r）
        String sql = String.format("LOAD DATA LOCAL INFILE '%s' INTO TABLE %s (%s) "
                        + "CHARACTER SET utf8mb4 FIELDS TERMINATED BY '\\t' ESCAPED BY '\\\\' LINES TERMINATED BY '\\n'",
                escapeSqlLiteral(file.toAbsolutePath().toString()), target, columnList);
        Long loaded = targetJdbc.execute((Statement stmt) -> {
            stmt.execute(sql);
            return (long) stmt.getUpdateCount();
        });
        return loaded == null ? 0 : loaded;
    }

    // ==================== 表结构工具 ====================

    /** SHOW CREATE TABLE 读源表 DDL，替换表名后在目标库执行（跨实例同样有效） */
    private void createTargetTableLikeSource(JdbcTemplate sourceJdbc, JdbcTemplate targetJdbc,
                                             String source, String target) {
        String ddl = sourceJdbc.query("SHOW CREATE TABLE " + source, rs -> {
            if (rs.next()) {
                return rs.getString(2);
            }
            return null;
        });
        if (ddl == null) {
            throw ArchiveException.validationError("无法读取源表 " + source + " 的建表语句（SHOW CREATE TABLE 无结果）");
        }
        String targetDdl = ddl.replaceFirst("(?i)CREATE\\s+TABLE\\s+`?" + java.util.regex.Pattern.quote(source) + "`?",
                "CREATE TABLE IF NOT EXISTS `" + target + "`");
        targetJdbc.execute(targetDdl);
    }

    private List<String> readColumnNames(String tableName) {
        return readColumnNames(sourceJdbc, tableName);
    }

    private List<String> readColumnNames(JdbcTemplate jdbc, String tableName) {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            return readColumnNames(metaData, catalog, tableName);
        } catch (SQLException e) {
            throw ArchiveException.validationError("读取表 " + tableName + " 列元数据失败：" + e.getMessage(), e);
        }
    }

    private List<String> readColumnNames(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        for (String candidate : new String[]{tableName, tableName.toLowerCase(), tableName.toUpperCase()}) {
            columns.clear();
            try (ResultSet rs = metaData.getColumns(catalog, null, candidate, null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
            if (!columns.isEmpty()) {
                return columns;
            }
        }
        return columns;
    }

    private boolean hasBinaryColumn(String tableName) {
        try (Connection connection = sourceJdbc.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (String candidate : new String[]{tableName, tableName.toLowerCase(), tableName.toUpperCase()}) {
                try (ResultSet rs = metaData.getColumns(catalog, null, candidate, null)) {
                    boolean found = false;
                    while (rs.next()) {
                        found = true;
                        int type = rs.getInt("DATA_TYPE");
                        if (type == java.sql.Types.BINARY || type == java.sql.Types.VARBINARY
                                || type == java.sql.Types.BLOB || type == java.sql.Types.LONGVARBINARY) {
                            return true;
                        }
                    }
                    if (found) {
                        return false;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            log.warn("检测二进制列失败（按无二进制处理）：{}", e.getMessage());
            return false;
        }
    }

    private boolean tableExists(JdbcTemplate jdbc, String tableName) {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (String candidate : new String[]{tableName, tableName.toLowerCase(), tableName.toUpperCase()}) {
                try (ResultSet tables = metaData.getTables(catalog, null, candidate, new String[]{"TABLE"})) {
                    if (tables.next()) {
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw ArchiveException.validationError("检查目标表 " + tableName + " 是否存在失败：" + e.getMessage(), e);
        }
    }

    // ==================== 通用工具 ====================

    private JdbcTemplate targetJdbc(ArchivePolicy policy) {
        if (resolver.isPrimary(policy.getConnectId())) {
            return sourceJdbc;
        }
        return resolver.targetJdbcTemplate(policy.getConnectId());
    }

    private String cacheKey(ArchivePolicy policy, String resolvedTarget) {
        return policy.getConnectId() == null ? "primary:" + resolvedTarget : policy.getConnectId() + ":" + resolvedTarget;
    }

    private List<String> diff(List<String> from, List<String> to) {
        List<String> result = new ArrayList<>();
        for (String c : from) {
            if (!to.contains(c)) {
                result.add(c);
            }
        }
        return result;
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return parts;
    }

    private static String joinPlaceholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private static String escapeSqlLiteral(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    private boolean isLocalInfileDisabled(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        Throwable cause = e;
        while (cause != null) {
            String m = cause.getMessage();
            if (m != null && (m.toLowerCase().contains("local infile") || m.toLowerCase().contains("allowloadlocalinfile"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return msg.contains("local infile");
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("清理归档临时文件失败：{}", file, e);
        }
    }

    /** 预留：dataSource 暴露（探测 URL 用） */
    public DataSource sourceDataSource() {
        return sourceJdbc.getDataSource();
    }

    /** 归档库物理连接（AUTO 模式探测目标 URL 用，调用方负责关闭） */
    public Connection resolveTargetConnection(String connectId) throws SQLException {
        return resolver.resolve(connectId).getConnection();
    }

    /** 归档库 JdbcTemplate（按策略连接解析；主库场景返回源连接模板） */
    public JdbcTemplate targetJdbcForPolicy(ArchivePolicy policy) {
        return targetJdbc(policy);
    }
}
