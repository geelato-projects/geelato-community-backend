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
     */
    public MqlScenarioResult executeScenario(MqlScenario scenario) {
        long start = System.currentTimeMillis();
        if (jdbcTemplate == null) {
            return MqlScenarioResult.fail(scenario, System.currentTimeMillis() - start,
                    "未配置数据源（JdbcTemplate 不可用）");
        }
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
     */
    public String initSchema() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(SCHEMASQL_PATH)) {
            if (is == null) {
                return "schema 文件未找到: " + SCHEMASQL_PATH;
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            // 移除注释行，按分号分割执行
            String[] statements = sql.split(";");
            int count = 0;
            for (String stmt : statements) {
                String trimmed = stmt.trim();
                // 跳过注释行和空语句
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                // 跳过纯注释行
                String[] lines = trimmed.split("\n");
                StringBuilder clean = new StringBuilder();
                for (String line : lines) {
                    if (!line.trim().startsWith("--")) {
                        clean.append(line).append("\n");
                    }
                }
                String cleanStmt = clean.toString().trim();
                if (!cleanStmt.isEmpty()) {
                    try {
                        jdbcTemplate.execute(cleanStmt);
                        count++;
                    } catch (Exception e) {
                        log.debug("建表语句执行（可能已存在）: {}", e.getMessage());
                    }
                }
            }
            return "初始化完成，执行 " + count + " 条语句";
        } catch (Exception e) {
            return "初始化失败: " + e.getMessage();
        }
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
