package cn.geelato.test.model;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 测试结果模型
 */
@Data
public class TestResult {
    /**
     * 测试ID
     */
    private String id;
    
    /**
     * 测试名称
     */
    private String name;
    
    /**
     * 测试描述
     */
    private String description;
    
    /**
     * 测试类名
     */
    private String className;
    
    /**
     * 测试方法名
     */
    private String methodName;
    
    /**
     * 测试开始时间
     */
    private Date startTime;
    
    /**
     * 测试结束时间
     */
    private Date endTime;
    
    /**
     * 测试耗时（毫秒）
     */
    private long duration;
    
    /**
     * 测试用例项结果列表
     */
    private List<TestCaseItem> testItems;
    
    /**
     * 总测试用例数
     */
    private int totalCount;
    
    /**
     * 通过测试用例数
     */
    private int passedCount;
    
    /**
     * 失败测试用例数
     */
    private int failedCount;
    
    /**
     * 测试是否全部通过
     */
    private boolean allPassed;
}