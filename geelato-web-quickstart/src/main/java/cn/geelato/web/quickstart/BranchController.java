package cn.geelato.web.quickstart;

import cn.geelato.lang.api.ApiResult;
import io.sentry.Sentry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import cn.geelato.web.common.annotation.ApiRestController;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@ApiRestController
public class BranchController {
    @GetMapping("/branch")
    public ApiResult<String> branch() throws IOException {
        Resource resource = new ClassPathResource("branch.properties");
        Properties properties = new Properties();
        try (InputStream in = resource.getInputStream()) {
            properties.load(in);
        }
        String value = properties.getProperty("build.branch");
        if (!StringUtils.hasText(value)) {
            return ApiResult.success("default");
        }
        return ApiResult.success(value);
    }

    @GetMapping("/sentry")
    public ApiResult<String> sentry() throws IOException {
        Resource resource = new ClassPathResource("properties/sentry.properties");
        Properties properties = new Properties();
        try (InputStream in = resource.getInputStream()) {
            properties.load(in);
        }
        StringBuilder sb = new StringBuilder();
        for (String name : properties.stringPropertyNames()) {
            sb.append(name).append("=").append(properties.getProperty(name)).append("\n");
        }
        return ApiResult.success(sb.toString().trim());
    }
}

