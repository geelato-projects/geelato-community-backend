package cn.geelato.mqltest.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * MQL 测试场景定义（从 mql-test-scenarios/*.json 加载）。
 * <p>
 * 每个场景包含：预置数据(setup) → 执行 MQL → 比对期望结果 → 清理(teardown)。
 */
@Getter
@Setter
public class MqlScenario {
    /** 场景唯一标识 */
    private String id;
    /** 场景名称 */
    private String name;
    /** 分组（WHERE过滤/JOIN关联/JSON列/视图/关键字组合） */
    private String category;
    /** 执行前预置数据的 SQL 数组 */
    private List<String> setup;
    /** 执行后清理数据的 SQL 数组 */
    private List<String> teardown;
    /** 待执行的 MQL JSON */
    private String mql;
    /** 期望返回行数（null 表示不校验行数） */
    private Integer expectRowCount;
    /** 期望首行数据（key=字段名，value=期望值；null 表示不校验字段值） */
    private Map<String, Object> expectFirstRow;
    /** 期望生成的 SQL 中包含的子串（null 表示不校验 SQL） */
    private String expectSqlContains;
    /** 描述 */
    private String description;
}
