package cn.geelato.mcp.common.service;

public interface ApiKeyValidationService {
    
    boolean validate(String apiKey, String clientIp);
    
    void recordUsage(String apiKey);
}
