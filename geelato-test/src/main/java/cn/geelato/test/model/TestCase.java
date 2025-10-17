package cn.geelato.test.model;

import lombok.Data;

import java.util.List;

/**
 * 测试用例模型
 */
@Data
public class TestCase {
    /**
     * 测试用例名称
     */
    private String name;
    
    /**
     * 测试用例描述
     */
    private String description;
    
    /**
     * 测试方法的完整类名
     */
    private String className;
    
    /**
     * 测试方法名
     */
    private String methodName;
    
    /**
     * 测试用例列表
     */
    private List<TestCaseItem> testItems;
}