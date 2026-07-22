package cn.geelato.mqltest.explain;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.mql.parser.JsonTextQueryParser;
import cn.geelato.core.sql.provider.MetaQuerySqlProvider;
import cn.geelato.mqltest.dto.MqlExecuteResult;
import cn.geelato.mqltest.dto.MqlScenario;
import cn.geelato.mqltest.dto.MqlScenarioResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MQL 测试场景服务：加载场景集 + 执行 + 结果比对。
 * <p>
 * 从 classpath 的 mql-test-scenarios/*.json 加载场景，在真实数据库上执行验证。
 */
@Slf4j
public class MqlTestScenarioService {

    private static final String SCENARIOS_DIR = "classpath:mql-test-scenarios/*.json";
    private static final String SCHEMASQL_PATH = "mql-test-schema.sql";

    private final JdbcTemplate jdbcTemplate;
    private final JsonTextQueryParser queryParser = new JsonTextQueryParser();

    public MqlTestScenarioService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== 场景加载 ====================

    /**
     * 加载全部场景（从 classpath 的 mql-test-scenarios/*.json）。
     */
    public List<MqlScenario> listScenarios() {
        List<MqlScenario> all = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(SCENARIOS_DIR);
            for (Resource resource : resources) {
                List<MqlScenario> scenarios = loadScenariosFromFile(resource);
                all.addAll(scenarios);
            }
        } catch (IOException e) {
            log.warn("加载场景集失败: {}", e.getMessage());
        }
        return all;
    }

    /**
     * 按分组返回场景。
     */
    public Map<String, List<MqlScenario>> listByCategory() {
        Map<String, List<MqlScenario>> grouped = new LinkedHashMap<>();
        for (MqlScenario s : listScenarios()) {
            String category = s.getCategory() != null ? s.getCategory() : "未分组";
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(s);
        }
        return grouped;
    }

    private List<MqlScenario> loadScenariosFromFile(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JSONArray array = JSON.parseArray(content);
            return array.toJavaList(MqlScenario.class);
        }
    }

    // ==================== 单次执行（不做比对） ====================

    /**
     * 执行单次 MQL 查询，返回真实结果行。
     */
    public MqlExecuteResult executeMql(String mqlJson) {
        long start = System.currentTimeMillis();
        MqlExecuteResult result = new MqlExecuteResult();
        try {
            QueryCommand command = queryParser.parse(mqlJson);
            MetaQuerySqlProvider provider = new MetaQuerySqlProvider();
            BoundSql boundSql = provider.generate(command);
            result.setEntityName(command.getEntityName());
            result.setSql(boundSql.getSql());
            result.setParams(boundSql.getParams());
            result.setTypes(boundSql.getTypes());
            result.setPagingQuery(command.isPagingQuery());

            // 真实执行
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    boundSql.getSql(), boundSql.getParams());
            result.setRows(rows);
            result.setRowCount(rows.size());

            // 分页时额外查 count
            if (command.isPagingQuery()) {
                String countSql = provider.buildCountSql(command);
                result.setCountSql(countSql);
            }
            result.setSuccess(true);
        } catch (Exception e) {
            log.warn("MQL 执行失败: {}", e.getMessage());
            result.setSuccess(false);
            result.setError(e.getMessage());
        }
        result.setElapsedMs(System.currentTimeMillis() - start);
        return result;
    }

    // ==================== 场景执行（含比对） ====================

    /**
     * 执行单个场景：setup → 执行 MQL → 比对 → teardown。
     * <p>
     * 场景集测试的是 MQL 语法与 SQL 生成的正确性，不应被平台的租户隔离/数据权限注入器干扰
     * （否则 setup 数据的 tenant_code/creator 与注入身份不匹配会导致查不到数据）。
     * 因此执行期间临时禁用 SPI 注入器（通过清空 BeansUtils 的 ApplicationContext，
     * 使 MqlQueryFilterRuntimeResolver 找不到注入器 Bean 而跳过 inject）。
     */
    public MqlScenarioResult executeScenario(MqlScenario scenario) {
        long start = System.currentTimeMillis();
        if (jdbcTemplate == null) {
            return MqlScenarioResult.fail(scenario, System.currentTimeMillis() - start,
                    "未配置数据源（JdbcTemplate 不可用）");
        }
        Object savedCtx = disableInjectorTemporarily();
        try {
            // 1. setup 预置数据
            runSqlList(scenario.getSetup());
            try {
                // 2. 执行 MQL 并比对
                MqlScenarioResult result = runAndVerify(scenario);
                result.setElapsedMs(System.currentTimeMillis() - start);
                return result;
            } finally {
                // 3. teardown 清理（即使失败也清理）
                runSqlList(scenario.getTeardown());
            }
        } catch (Exception e) {
            log.warn("场景 {} 执行异常: {}", scenario.getId(), e.getMessage());
            MqlScenarioResult r = MqlScenarioResult.fail(scenario, System.currentTimeMillis() - start, e.getMessage());
            // 尝试清理
            safeRunSqlList(scenario.getTeardown());
            return r;
        } finally {
            restoreInjectorContext(savedCtx);
        }
    }

    /**
     * 临时禁用 MQL SPI 注入器：通过反射清空 BeansUtils 的静态 ApplicationContext，
     * 使 MqlQueryFilterRuntimeResolver.resolveUniqueInjectorEntry 返回 null（找不到注入器）。
     * 返回原 context 供恢复使用。
     */
    private Object disableInjectorTemporarily() {
        try {
            java.lang.reflect.Field f = cn.geelato.core.util.BeansUtils.class.getDeclaredField("context");
            f.setAccessible(true);
            Object original = f.get(null);
            f.set(null, null);
            return original;
        } catch (Exception e) {
            log.debug("禁用注入器失败（非致命）: {}", e.getMessage());
            return null;
        }
    }

    private void restoreInjectorContext(Object original) {
        try {
            java.lang.reflect.Field f = cn.geelato.core.util.BeansUtils.class.getDeclaredField("context");
            f.setAccessible(true);
            f.set(null, original);
        } catch (Exception e) {
            log.debug("恢复注入器 context 失败（非致命）: {}", e.getMessage());
        }
    }

    /**
     * 批量执行全部场景，返回汇总。
     */
    public Map<String, Object> runAllScenarios() {
        List<MqlScenario> scenarios = listScenarios();
        List<MqlScenarioResult> results = new ArrayList<>();
        int passed = 0;
        int failed = 0;
        for (MqlScenario scenario : scenarios) {
            MqlScenarioResult result = executeScenario(scenario);
            results.add(result);
            if (result.isPassed()) {
                passed++;
            } else {
                failed++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", scenarios.size());
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("results", results);
        return summary;
    }

    // ==================== 内部方法 ====================

    private MqlScenarioResult runAndVerify(MqlScenario scenario) {
        MqlExecuteResult execResult = executeMql(scenario.getMql());
        if (!execResult.isSuccess()) {
            MqlScenarioResult fail = MqlScenarioResult.fail(scenario, 0, execResult.getError());
            fail.setSql(execResult.getSql());
            return fail;
        }

        MqlScenarioResult result = MqlScenarioResult.base(scenario, 0);
        result.setSql(execResult.getSql());
        result.setExecuted(true);
        result.setActualRowCount(execResult.getRowCount());
        if (!execResult.getRows().isEmpty()) {
            result.setActualFirstRow(execResult.getRows().get(0));
        }
        if (scenario.getExpectRowCount() != null) {
            result.setExpectedRowCount(scenario.getExpectRowCount());
        }

        boolean allPassed = true;

        // 校验 expectRowCount
        if (scenario.getExpectRowCount() != null) {
            if (execResult.getRowCount() == scenario.getExpectRowCount()) {
                result.getDetails().add("行数校验通过: " + execResult.getRowCount());
            } else {
                result.getDetails().add(String.format("行数不匹配: 期望 %d, 实际 %d",
                        scenario.getExpectRowCount(), execResult.getRowCount()));
                allPassed = false;
            }
        }

        // 校验 expectFirstRow
        if (scenario.getExpectFirstRow() != null && !execResult.getRows().isEmpty()) {
            Map<String, Object> firstRow = execResult.getRows().get(0);
            for (Map.Entry<String, Object> entry : scenario.getExpectFirstRow().entrySet()) {
                String field = entry.getKey();
                Object expected = entry.getValue();
                Object actual = firstRow.get(field);
                if (Objects.equals(stringify(expected), stringify(actual))) {
                    result.getDetails().add("字段校验通过: " + field + "=" + actual);
                } else {
                    result.getDetails().add(String.format("字段 %s 不匹配: 期望 '%s', 实际 '%s'",
                            field, expected, actual));
                    allPassed = false;
                }
            }
        }

        // 校验 expectSqlContains
        if (StringUtils.hasText(scenario.getExpectSqlContains()) && StringUtils.hasText(execResult.getSql())) {
            String normalizedSql = execResult.getSql().replaceAll("\\s+", " ");
            String normalizedExpect = scenario.getExpectSqlContains().replaceAll("\\s+", " ");
            if (normalizedSql.contains(normalizedExpect)) {
                result.getDetails().add("SQL片段校验通过: " + scenario.getExpectSqlContains());
            } else {
                result.getDetails().add("SQL片段不匹配: 期望包含 '" + scenario.getExpectSqlContains() + "'");
                allPassed = false;
            }
        }

        result.setPassed(allPassed);
        return result;
    }

    private void runSqlList(List<String> sqlList) {
        if (sqlList == null || sqlList.isEmpty()) {
            return;
        }
        for (String sql : sqlList) {
            if (StringUtils.hasText(sql)) {
                jdbcTemplate.execute(sql);
            }
        }
    }

    private void safeRunSqlList(List<String> sqlList) {
        if (sqlList == null) return;
        for (String sql : sqlList) {
            try {
                if (StringUtils.hasText(sql)) {
                    jdbcTemplate.execute(sql);
                }
            } catch (Exception e) {
                log.debug("teardown 清理失败（忽略）: {}", e.getMessage());
            }
        }
    }

    private static String stringify(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    // ==================== 建表与清理 ====================

    /**
     * 初始化测试表结构（执行 mql-test-schema.sql）。
     * 返回每条语句的执行结果（成功/失败原因），便于排查。
     */
    public String initSchema() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(SCHEMASQL_PATH)) {
            if (is == null) {
                return "schema 文件未找到: " + SCHEMASQL_PATH;
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            List<String> statements = splitSqlStatements(sql);
            StringBuilder result = new StringBuilder();
            int success = 0;
            int failed = 0;
            for (int i = 0; i < statements.size(); i++) {
                String stmt = statements.get(i).trim();
                if (stmt.isEmpty()) continue;
                try {
                    jdbcTemplate.execute(stmt);
                    success++;
                } catch (Exception e) {
                    failed++;
                    String errMsg = e.getMessage();
                    // 截取根因（取第一行）
                    if (errMsg != null && errMsg.contains("\n")) {
                        errMsg = errMsg.substring(0, errMsg.indexOf('\n'));
                    }
                    result.append(String.format("[语句%d 失败] %s\n  SQL: %s\n  错误: %s\n\n",
                            i + 1, summarizeStmt(stmt), abbreviate(stmt, 80), errMsg));
                    log.warn("建表语句执行失败: SQL={}, err={}", abbreviate(stmt, 100), errMsg);
                }
            }
            result.insert(0, String.format("初始化完成：成功 %d 条，失败 %d 条。\n\n", success, failed));
            return result.toString().trim();
        } catch (Exception e) {
            log.error("初始化测试表异常", e);
            return "初始化失败: " + e.getMessage();
        }
    }

    /**
     * 按分号分割 SQL，移除注释行，跳过空语句。
     * 注意：简单的分号分割对含分号的字符串字面值不安全，但 schema.sql 不含此类情况。
     */
    private List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmedLine = line.trim();
            // 跳过整行注释
            if (trimmedLine.startsWith("--")) {
                continue;
            }
            current.append(line).append("\n");
            // 按分号结尾分割（分号在行尾的常见情况）
            if (trimmedLine.endsWith(";")) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    // 去掉末尾分号（execute 不需要）
                    if (stmt.endsWith(";")) {
                        stmt = stmt.substring(0, stmt.length() - 1).trim();
                    }
                    statements.add(stmt);
                }
                current.setLength(0);
            }
        }
        // 处理末尾无分号的残余
        String tail = current.toString().trim();
        if (!tail.isEmpty() && !tail.endsWith(";")) {
            statements.add(tail.endsWith(";") ? tail.substring(0, tail.length() - 1) : tail);
        }
        return statements;
    }

    /** 提取 SQL 类型与操作对象（CREATE TABLE mql_test_user / CREATE FUNCTION gfn_xxx） */
    private String summarizeStmt(String stmt) {
        String firstLine = stmt.split("\n")[0].trim().replaceAll("\\s+", " ");
        return abbreviate(firstLine, 60);
    }

    private String abbreviate(String s, int max) {
        if (s == null) return "";
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() > max ? oneLine.substring(0, max) + "..." : oneLine;
    }

    /**
     * 清理全部测试表数据（谨慎操作）。
     */
    public String cleanupAllData() {
        String[] tables = {"mql_test_order_item", "mql_test_order", "mql_test_user", "mql_test_org"};
        StringBuilder sb = new StringBuilder();
        for (String table : tables) {
            try {
                int rows = jdbcTemplate.update("DELETE FROM " + table);
                sb.append(table).append(":").append(rows).append(" ");
            } catch (Exception e) {
                sb.append(table).append(":错误 ");
            }
        }
        return "清理完成: " + sb;
    }
}
