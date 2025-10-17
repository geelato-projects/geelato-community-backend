package cn.geelato.test.model;

import lombok.Data;

import java.util.Date;

/**
 * 测试接口信息模型
 */
@Data
public class TestApiInfo {
    /**
     * 服务名称
     */
    private String serviceName;
    
    /**
     * 服务地址
     */
    private String serviceUrl;
    
    /**
     * 测试输入
     */
    private String testInput;
    
    /**
     * 预期结果
     */
    private String expectedResult;
    
    /**
     * 最近测试时间
     */
    private Date lastTestTime;
    
    /**
     * 测试结果
     */
    private String testResult;
    
    /**
     * 是否通过测试
     */
    private boolean passed;
    
    /**
     * 类名
     */
    private String className;
    
    /**
     * 方法名
     */
    private String methodName;
}