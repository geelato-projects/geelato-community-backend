package cn.geelato.datasource;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class DbHostMapFileLoader {
    private static final String ENV_FILE_PATH = "GEELATO_DS_HOST_MAP_FILE";
    private static final String DEFAULT_RELATIVE_PATH = "conf/db-host-map.txt";

    private volatile long lastModifiedMs = -1L;
    private volatile Map<String, HostPort> mappings = Collections.emptyMap();

    public HostPort resolve(String sourceHost, Integer sourcePort) {
        if (sourceHost == null || sourceHost.trim().isEmpty()) {
            return null;
        }
        reloadIfNeeded();
        if (mappings.isEmpty()) {
            return null;
        }
        if (sourcePort != null) {
            HostPort p = mappings.get(sourceHost + ":" + sourcePort);
            if (p != null) {
                return p;
            }
        }
        return mappings.get(sourceHost);
    }

    private void reloadIfNeeded() {
        Path file = resolveFilePath();
        if (file == null || !Files.isRegularFile(file)) {
            if (lastModifiedMs != -1L) {
                lastModifiedMs = -1L;
                mappings = Collections.emptyMap();
            }
            return;
        }
        long lm;
        try {
            lm = Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            return;
        }
        if (lm == lastModifiedMs) {
            return;
        }
        Map<String, HostPort> loaded = load(file);
        mappings = loaded;
        lastModifiedMs = lm;
    }

    private Path resolveFilePath() {
        String fromEnv = System.getenv(ENV_FILE_PATH);
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return Path.of(fromEnv.trim());
        }
        return Path.of(DEFAULT_RELATIVE_PATH);
    }

    private Map<String, HostPort> load(Path file) {
        Map<String, HostPort> map = new HashMap<>();
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#") || s.startsWith("//")) {
                    continue;
                }
                int idx = s.indexOf('=');
                if (idx <= 0 || idx >= s.length() - 1) {
                    continue;
                }
                String source = s.substring(0, idx).trim();
                String target = s.substring(idx + 1).trim();
                HostPort targetHp = parseHostPort(target);
                if (targetHp == null || targetHp.host == null || targetHp.host.isEmpty()) {
                    continue;
                }
                map.put(source, targetHp);
            }
        } catch (Exception ignored) {
        }
        return map.isEmpty() ? Collections.emptyMap() : map;
    }

    public static HostPort parseHostPort(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        if (s.isEmpty()) {
            return null;
        }
        int idx = s.lastIndexOf(':');
        if (idx <= 0 || idx == s.length() - 1) {
            return new HostPort(s, null);
        }
        String host = s.substring(0, idx).trim();
        String portStr = s.substring(idx + 1).trim();
        try {
            return new HostPort(host, Integer.parseInt(portStr));
        } catch (Exception e) {
            return new HostPort(s, null);
        }
    }

    public record HostPort(String host, Integer port) {
    }
}

