package cn.geelato.web.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.data.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.Set;

public class PlatformEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment  environment, SpringApplication application) {

//        Properties properties = new Properties();
//        properties.put("geelato.oss.endPoint","123");
//        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("my",properties);
//        environment.getPropertySources().addFirst(propertiesPropertySource);
    }
}
