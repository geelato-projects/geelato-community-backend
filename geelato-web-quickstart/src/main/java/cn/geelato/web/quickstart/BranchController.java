package cn.geelato.web.quickstart;

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
    public String branch() throws IOException {
        Resource resource = new ClassPathResource("branch.properties");
        Properties properties = new Properties();
        try (InputStream in = resource.getInputStream()) {
            properties.load(in);
        }
        String value = properties.getProperty("build.branch");
        if (!StringUtils.hasText(value)) {
            return "master";
        }
        return value;
    }
}

