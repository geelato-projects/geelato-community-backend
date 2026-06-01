package cn.geelato.web.common.traffic;

import cn.geelato.security.User;
import cn.geelato.traffic.TrafficTagStrategy;
import cn.geelato.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Component
public class WhitelistTrafficTagStrategy implements TrafficTagStrategy {
    private final TrafficColoringProperties properties;
    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    private volatile Set<String> cachedWhitelist;

    public WhitelistTrafficTagStrategy(TrafficColoringProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolveTag(HttpServletRequest request, User user) {
        if (properties == null) {
            return null;
        }
        if (user == null || StringUtils.isEmpty(user.getLoginName())) {
            return properties.getDefaultTag();
        }
        Set<String> whitelist = getWhitelist();
        if (whitelist.isEmpty()) {
            return properties.getDefaultTag();
        }
        String loginName = user.getLoginName().trim();
        return whitelist.contains(loginName) ? properties.getGrayTag() : properties.getDefaultTag();
    }

    private Set<String> getWhitelist() {
        Set<String> local = cachedWhitelist;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedWhitelist != null) {
                return cachedWhitelist;
            }
            Set<String> loaded = new LinkedHashSet<>();
            loadFromInline(loaded);
            loadFromLocation(loaded);
            cachedWhitelist = Collections.unmodifiableSet(loaded);
            return cachedWhitelist;
        }
    }

    private void loadFromInline(Set<String> target) {
        String inline = properties.getGrayWhitelist();
        if (StringUtils.isEmpty(inline)) {
            return;
        }
        String[] lines = inline.split("\\r?\\n");
        for (String line : lines) {
            addLine(target, line);
        }
    }

    private void loadFromLocation(Set<String> target) {
        String location = properties.getGrayWhitelistLocation();
        if (StringUtils.isEmpty(location)) {
            return;
        }
        try {
            Resource[] resources = resolver.getResources(location);
            for (Resource resource : resources) {
                if (resource == null || !resource.exists()) {
                    continue;
                }
                try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        addLine(target, line);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("load traffic gray whitelist failed, location={}", location, e);
        }
    }

    private void addLine(Set<String> target, String line) {
        if (line == null) {
            return;
        }
        String v = line.trim();
        if (v.isEmpty() || v.startsWith("#")) {
            return;
        }
        target.add(v);
    }
}
