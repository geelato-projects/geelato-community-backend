package cn.geelato.mqltest.explain;

import cn.geelato.mqltest.dto.MqlScenario;
import cn.geelato.mqltest.support.MqlTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * MqlTestScenarioService 单元测试。
 * <p>
 * 验证场景加载与分组逻辑（不依赖真实数据库）。
 */
@DisplayName("MQL 场景服务")
class MqlTestScenarioServiceTest extends MqlTestSupport {

    private MqlTestScenarioService serviceWithMock() {
        // 用 mock JdbcTemplate 创建 service（listScenarios 不依赖它）
        return new MqlTestScenarioService(mock(JdbcTemplate.class));
    }

    @Test
    @DisplayName("加载全部场景集文件")
    void listScenarios() {
        MqlTestScenarioService service = serviceWithMock();
        List<MqlScenario> scenarios = service.listScenarios();
        assertNotNull(scenarios);
        // 5 个文件：where-operators(14) + keywords(7) + join(4) + json-column(3) + view(2)
        assertTrue(scenarios.size() >= 25, "应加载至少25个场景，实际: " + scenarios.size());
    }

    @Test
    @DisplayName("场景含必需字段")
    void scenarioHasRequiredFields() {
        MqlTestScenarioService service = serviceWithMock();
        List<MqlScenario> scenarios = service.listScenarios();
        assertFalse(scenarios.isEmpty());
        for (MqlScenario s : scenarios) {
            assertNotNull(s.getId(), "场景 id 不能为空");
            assertNotNull(s.getName(), "场景 name 不能为空: " + s.getId());
            assertNotNull(s.getMql(), "场景 mql 不能为空: " + s.getId());
            assertNotNull(s.getCategory(), "场景 category 不能为空: " + s.getId());
        }
    }

    @Test
    @DisplayName("按分组返回场景")
    void listByCategory() {
        MqlTestScenarioService service = serviceWithMock();
        Map<String, List<MqlScenario>> grouped = service.listByCategory();
        assertNotNull(grouped);
        assertTrue(grouped.containsKey("WHERE过滤"));
        assertTrue(grouped.containsKey("JOIN关联"));
        assertTrue(grouped.containsKey("JSON列"));
    }

    @Test
    @DisplayName("WHERE过滤分组含14个操作符场景")
    void whereOperatorScenariosCount() {
        MqlTestScenarioService service = serviceWithMock();
        Map<String, List<MqlScenario>> grouped = service.listByCategory();
        List<MqlScenario> whereScenarios = grouped.get("WHERE过滤");
        assertNotNull(whereScenarios);
        assertEquals(14, whereScenarios.size(), "WHERE过滤应有14个操作符场景");
    }

    @Test
    @DisplayName("场景 setup/teardown SQL 存在")
    void scenariosHaveSetupTeardown() {
        MqlTestScenarioService service = serviceWithMock();
        List<MqlScenario> scenarios = service.listScenarios();
        for (MqlScenario s : scenarios) {
            assertNotNull(s.getSetup(), "setup 不能为空: " + s.getId());
            assertFalse(s.getSetup().isEmpty(), "setup 不能为空列表: " + s.getId());
            assertNotNull(s.getTeardown(), "teardown 不能为空: " + s.getId());
        }
    }

    @Test
    @DisplayName("场景 expectRowCount 或 expectSqlContains 至少有一个")
    void scenariosHaveExpectations() {
        MqlTestScenarioService service = serviceWithMock();
        List<MqlScenario> scenarios = service.listScenarios();
        for (MqlScenario s : scenarios) {
            assertTrue(s.getExpectRowCount() != null || s.getExpectSqlContains() != null,
                    "场景应至少有行数或SQL片段期望: " + s.getId());
        }
    }
}
