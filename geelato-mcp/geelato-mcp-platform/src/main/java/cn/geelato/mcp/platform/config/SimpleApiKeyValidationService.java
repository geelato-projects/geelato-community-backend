package cn.geelato.mcp.platform.config;

import cn.geelato.mcp.common.service.ApiKeyValidationService;
import org.springframework.stereotype.Service;

@Service
public class SimpleApiKeyValidationService implements ApiKeyValidationService {

    @Override
    public boolean validate(String apiKey, String clientIp) {
        // 简单实现：总是返回 true
        // 生产环境应该查询数据库验证
        return true;
    }

    @Override
    public void recordUsage(String apiKey) {
        // 简单实现：不做任何操作
        // 生产环境应该记录使用日志
    }
}
