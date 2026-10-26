package cn.geelato.script.kit.sql;

import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨实例/异构联邦执行：Calcite 内嵌连接按 connectId 挂 JdbcSchema（执行库挂保留名 local），
 * 执行改写后的限定名 SQL。单源过滤/投影由 Calcite 下推各库，跨源 join/聚合在应用内存执行
 * （非分布式引擎，大结果集受 sqlRowLimit 与超时约束）。每次查询独立 CalciteConnection，不缓存。
 */
@Slf4j
public class CalciteGateway {

    private static final String CALCITE_URL = "jdbc:calcite:lex=MYSQL;conformance=LENIENT";

    private final ConnectPoolProvider poolProvider;

    public CalciteGateway(ConnectPoolProvider poolProvider) {
        this.poolProvider = poolProvider == null ? ConnectPoolProvider.defaultProvider() : poolProvider;
    }

    public List<Map<String, Object>> query(CrossDbAnalyzer.Analysis analysis, DataSource executingDataSource,
                                           Map<String, Object> params, int maxRows, int timeoutMs, String execId) {
        String sql = analysis.federatedSql();
        try (Connection connection = DriverManager.getConnection(CALCITE_URL)) {
            CalciteConnection calcite = connection.unwrap(CalciteConnection.class);
            SchemaPlus root = calcite.getRootSchema();
            ExecutingTarget target = analysis.target();
            mount(root, CrossDbAnalyzer.LOCAL_MOUNT, executingDataSource, target.dbType(), target.db(), null);
            for (Map.Entry<String, TableLocation> entry : analysis.mounts().entrySet()) {
                DataSource pool = poolProvider.pool(entry.getKey());
                if (pool == null) {
                    throw new IllegalArgumentException("联邦查询挂载失败: 连接 " + entry.getKey()
                            + " 无可用连接池（ConnectPoolProvider 未覆盖该 connectId；带动态连接的平台宿主应装配"
                            + " orm Registry 支撑的 provider），涉及表 " + involvedTables(analysis, entry.getKey()));
                }
                TableLocation location = entry.getValue();
                mount(root, entry.getKey(), pool, location.dbType(), location.catalog(), location.qualifier());
            }
            log.info("KIT-XDB exec={} calcite 联邦执行 mounts={} 改写后 :: {}", execId, mountNames(analysis),
                    TraceSummary.of(sql));
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                Object[] args = NamedParamSupport.bindOrder(params, analysis.masked().order());
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == null) {
                        ps.setNull(i + 1, Types.NULL);
                    } else {
                        ps.setObject(i + 1, args[i]);
                    }
                }
                int finalMaxRows = maxRows <= 0 ? 1 : maxRows;
                tryQuietly(() -> ps.setMaxRows(finalMaxRows));
                tryQuietly(() -> ps.setQueryTimeout((int) Math.ceil(timeoutMs / 1000.0)));
                try (ResultSet rs = ps.executeQuery()) {
                    return readRows(rs, finalMaxRows);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("联邦查询执行失败: " + rootCause(e)
                    + " :: " + TraceSummary.of(sql), e);
        }
    }

    /**
     * 挂载策略：create(parent, name, ds, catalog, schema)。MySQL 用 catalog=库名；PG 跨 database 由
     * 独立连接天然隔离，按 db_schema 挂单 schema；H2/未知 catalog 与 schema 置 null（表直挂挂载名下）。
     */
    private void mount(SchemaPlus root, String name, DataSource dataSource, String dbType,
                       String catalog, String schema) throws SQLException {
        if (root.getSubSchema(name) == null) {
            if ("postgresql".equalsIgnoreCase(dbType) || "postgres".equalsIgnoreCase(dbType)) {
                root.add(name, JdbcSchema.create(root, name, dataSource, null,
                        schema == null || schema.isBlank() ? "public" : schema));
            } else if ("mysql".equalsIgnoreCase(dbType) || "mariadb".equalsIgnoreCase(dbType)) {
                root.add(name, JdbcSchema.create(root, name, dataSource, catalog, null));
            } else {
                root.add(name, JdbcSchema.create(root, name, dataSource, null, null));
            }
        }
    }

    private List<Map<String, Object>> readRows(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columnCount = md.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next() && rows.size() < maxRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String label = md.getColumnLabel(i);
                row.put(label == null || label.isBlank() ? md.getColumnName(i) : label, rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    private static String involvedTables(CrossDbAnalyzer.Analysis analysis, String connectId) {
        StringBuilder sb = new StringBuilder();
        for (CrossDbAnalyzer.RefDecision d : analysis.decisions().values()) {
            if (d.location() != null && connectId.equals(d.location().connectId())) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(d.name());
            }
        }
        return sb.toString();
    }

    /** 挂载名清单：local（执行库）+ 各跨库连接。 */
    private static String mountNames(CrossDbAnalyzer.Analysis analysis) {
        StringBuilder sb = new StringBuilder(CrossDbAnalyzer.LOCAL_MOUNT);
        for (String connectId : analysis.mounts().keySet()) {
            sb.append(',').append(connectId);
        }
        return sb.toString();
    }

    private static String rootCause(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }

    private static void tryQuietly(JdbcOp op) {
        try {
            op.run();
        } catch (Exception e) {
            log.debug("calcite statement 限制设置忽略: {}", e.getMessage());
        }
    }

    @FunctionalInterface
    private interface JdbcOp {
        void run() throws Exception;
    }

    /** 摘要护栏：错误信息里的 SQL 截断，避免异常文案爆炸。 */
    private static final class TraceSummary {
        static String of(String sql) {
            String one = sql == null ? "" : sql.replace("\r", " ").replace("\n", " ").trim();
            return one.length() <= 500 ? one : one.substring(0, 500) + "...(已截断)";
        }
    }
}
