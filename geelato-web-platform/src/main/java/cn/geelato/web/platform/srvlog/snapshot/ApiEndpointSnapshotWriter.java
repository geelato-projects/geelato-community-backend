package cn.geelato.web.platform.srvlog.snapshot;

import cn.geelato.web.common.annotation.ApiRestController;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class ApiEndpointSnapshotWriter {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final RequestMappingHandlerMapping handlerMapping;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public ApiEndpointSnapshotWriter(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping, ObjectMapper objectMapper, Environment environment) {
        this.handlerMapping = handlerMapping;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @PostConstruct
    public void writeSnapshot() {
        Path dir = resolveDir();
        if (dir == null) {
            return;
        }
        List<ApiEndpointSnapshot> snapshots = scan();
        if (snapshots.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
            return;
        }
        String ts = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).format(TS);
        Path file = dir.resolve("api-snapshot-" + ts + ".json");
        try {
            String json = objectMapper.writeValueAsString(snapshots);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private List<ApiEndpointSnapshot> scan() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        List<ApiEndpointSnapshot> list = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : handlerMethods.entrySet()) {
            HandlerMethod hm = e.getValue();
            Class<?> beanType = hm.getBeanType();
            if (!AnnotatedElementUtils.hasAnnotation(beanType, ApiRestController.class)) {
                continue;
            }
            ApiRestController arc = AnnotatedElementUtils.findMergedAnnotation(beanType, ApiRestController.class);
            String category = arc == null ? null : arc.category();
            RequestMappingInfo info = e.getKey();
            Set<RequestMethod> requestMethods = info.getMethodsCondition().getMethods();
            Set<String> patterns = resolvePatterns(info);
            List<String> candidates = new ArrayList<>();
            if (requestMethods.isEmpty()) {
                for (String p : patterns) {
                    candidates.add("* " + p);
                }
            } else {
                for (RequestMethod rm : requestMethods) {
                    for (String p : patterns) {
                        candidates.add(rm.name() + " " + p);
                    }
                }
            }
            candidates.sort(Comparator.naturalOrder());
            ApiEndpointSnapshot s = new ApiEndpointSnapshot(
                    beanType.getName(),
                    hm.getMethod().getName(),
                    hm.getMethod().toGenericString(),
                    Collections.unmodifiableList(candidates),
                    category
            );
            list.add(s);
        }
        list.sort(Comparator.comparing(ApiEndpointSnapshot::controllerClass).thenComparing(ApiEndpointSnapshot::methodName));
        return list;
    }

    private Set<String> resolvePatterns(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        if (info.getPatternsCondition() != null) {
            return info.getPatternsCondition().getPatterns();
        }
        return Collections.emptySet();
    }

    private Path resolveDir() {
        String dir = environment.getProperty("geelato.api-snapshot.dir");
        if (dir != null && !dir.isBlank()) {
            return Path.of(dir);
        }
        String root = environment.getProperty("geelato.file.root.path");
        if (root == null || root.isBlank()) {
            return null;
        }
        return Path.of(root, "logs", "api-snapshot");
    }

    private record ApiEndpointSnapshot(String controllerClass,
                                       String methodName,
                                       String handlerSignature,
                                       List<String> methodKeyCandidates,
                                       String apiCategory) {
    }
}

