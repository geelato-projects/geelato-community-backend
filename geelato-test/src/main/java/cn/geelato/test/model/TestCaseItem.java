package cn.geelato.test.model;

import lombok.Data;

/**
 * 测试用例项
 * 表示单个测试用例的输入参数和预期结果
 */
@Data
public class TestCaseItem {
    /**
     * 测试用例项ID
     */
    private String id;
    
    /**
     * 测试用例项描述
     */
    private String description;
    
    /**
     * 输入参数，JSON格式
     */
    private String input;
    
    /**
     * 预期结果，JSON格式
     */
    private String expectedOutput;
    
    /**
     * 实际结果，JSON格式
     * 在测试执行后填充
     */
    private String actualOutput;
    
    /**
     * 测试结果
     * true: 通过
     * false: 失败
     */
    private Boolean passed;
    
    /**
     * 错误信息
     * 当测试失败时填充
     */
    private String errorMessage;
}