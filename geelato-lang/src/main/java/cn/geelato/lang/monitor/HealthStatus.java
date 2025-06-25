package cn.geelato.lang.monitor;

import lombok.Getter;
import lombok.Setter;

/**
 * 健康状态信息
 */
@Setter
@Getter
public class HealthStatus {

    // Getters and Setters
    /**
     * 模块名称
     */
    private String module;
    
    /**
     * 健康状态
     */
    private Status status;
    
    /**
     * 详细信息
     */
    private String details;
    
    /**
     * 检查时间
     */
    private long timestamp;
    
    public HealthStatus() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public HealthStatus(String module, Status status) {
        this.module = module;
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }
    
    public HealthStatus(String module, Status status, String details) {
        this.module = module;
        this.status = status;
        this.details = details;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 健康状态枚举
     */
    @Getter
    public enum Status {
        HEALTH("健康"),
        ABNORMAL("异常"),
        UNKNOWN("未知");
        
        private final String description;
        
        Status(String description) {
            this.description = description;
        }

    }
}