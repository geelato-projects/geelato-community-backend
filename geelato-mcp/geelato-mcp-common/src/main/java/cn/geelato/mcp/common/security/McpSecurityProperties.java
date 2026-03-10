package cn.geelato.mcp.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "geelato.mcp.security")
public class McpSecurityProperties {
    
    private String authType = "hybrid";
    
    private ApiKey apiKey = new ApiKey();
    
    private Jwt jwt = new Jwt();
    
    @Data
    public static class ApiKey {
        private String headerName = "X-API-Key";
        private List<String> keys = new ArrayList<>();
        private boolean enabled = true;
        private boolean useDatabase = false;
    }
    
    @Data
    public static class Jwt {
        private String headerName = "Authorization";
        private String prefix = "Bearer ";
        private String signKey;
        private boolean enabled = true;
    }
}
