package cn.geelato.lang.monitor;

/**
 * 健康检查端点接口
 * 用于定义系统模块的健康检查标准
 */
public interface HealthEndpoint {
    
    /**
     * 检查模块健康状态
     * @return 健康状态信息
     */
    HealthStatus checkHealthStatus();
}