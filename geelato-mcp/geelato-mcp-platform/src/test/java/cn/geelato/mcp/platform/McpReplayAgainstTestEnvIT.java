package cn.geelato.mcp.platform;

import cn.geelato.it.support.goreplay.GoreplayRunner;
import cn.geelato.it.support.replaydiff.DiffReport;
import cn.geelato.it.support.replaydiff.DiffIgnoreConfig;
import cn.geelato.it.support.replaydiff.BodyCaptureConfig;
import cn.geelato.it.support.replaydiff.ProxyResponseMode;
import cn.geelato.it.support.replaydiff.ReplayDiffProxy;
import cn.geelato.it.support.replaydiff.ReplayDiffProxyConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.BufferedReader;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class McpReplayAgainstTestEnvIT extends McpPlatformMysqlContainerSupport {

    private static ConfigurableApplicationContext context;
    private static int port;

    @BeforeAll
    static void startServer() {
        assumeMysqlStarted();
        String[] args = concat(
                new String[]{"--server.port=0", "--spring.profiles.active=test"},
                mysqlDatasourceArgs()
        );
        context = SpringApplication.run(McpPlatformApplication.class, args);
        port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
    }

    @AfterAll
    static void stopServer() {
        if (context != null) {
            context.close();
        }
        stopMysqlIfStarted();
    }

    @Test
    void replayAndDiff() throws Exception {
        String remoteBaseUrl = System.getProperty("remoteBaseUrl");
        Assumptions.assumeTrue(remoteBaseUrl != null && !remoteBaseUrl.isBlank(), "remoteBaseUrl not set, skip");

        String localBaseUrl = "http://127.0.0.1:" + port;

        Path reportDir = Paths.get("target", "replay-diff");
        Files.createDirectories(reportDir);
        Path reportFile = reportDir.resolve("diff-report.jsonl");
        Files.deleteIfExists(reportFile);

        int proxyPort = findFreePort();
        URI baseline = URI.create(trimTrailingSlash(remoteBaseUrl));
        URI candidate = URI.create(localBaseUrl);

        Set<String> allowMethods = parseAllowMethods(System.getProperty("replay.allowMethods", "GET,HEAD,OPTIONS"));
        ReplayDiffProxyConfig proxyConfig = new ReplayDiffProxyConfig(
                "127.0.0.1",
                proxyPort,
                baseline,
                candidate,
                reportFile,
                DiffIgnoreConfig.empty(),
                ProxyResponseMode.CANDIDATE,
                Integer.parseInt(System.getProperty("diff.maxJsonDiffEntries", "200")),
                BodyCaptureConfig.defaults(),
                allowMethods,
                Set.of(baseline.getHost())
        );

        String executable = System.getProperty("goreplay.executable", "goreplay");
        GoreplayRunner.assumeGoreplayAvailable(executable);

        List<String> args = buildGoreplayArgs(System.getProperties(), "http://127.0.0.1:" + proxyPort);
        boolean hasInput = args.stream().anyMatch(a -> a.startsWith("--input"));
        Assumptions.assumeTrue(hasInput, "goreplay input not configured, skip");

        Duration timeout = Duration.ofSeconds(Long.parseLong(System.getProperty("goreplay.timeoutSeconds", "120")));

        int exitCode;
        try (ReplayDiffProxy proxy = new ReplayDiffProxy(proxyConfig)) {
            proxy.start();
            GoreplayRunner runner = new GoreplayRunner(executable, null, Map.of());
            exitCode = runner.runAndWait(args, timeout);
        }

        Assertions.assertEquals(0, exitCode, "goreplay exited with non-zero code: " + exitCode);

        ReplaySummary summary = summarize(reportFile);

        int diffMax = Integer.parseInt(System.getProperty("diff.max", "0"));
        System.out.println("replay-diff summary: total=" + summary.total + ", diff=" + summary.diff + ", reportFile=" + reportFile.toAbsolutePath());
        Assertions.assertTrue(summary.diff <= diffMax, "diff=" + summary.diff + " > diff.max=" + diffMax + ", reportDir=" + reportDir.toAbsolutePath());
    }

    private static ReplaySummary summarize(Path reportFile) throws Exception {
        if (!Files.exists(reportFile)) {
            return new ReplaySummary(0, 0);
        }
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        long total = 0;
        long diff = 0;
        try (BufferedReader reader = Files.newBufferedReader(reportFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                total++;
                DiffReport report = mapper.readValue(line, DiffReport.class);
                if (report == null || report.comparison() == null) {
                    diff++;
                    continue;
                }
                boolean ok = report.comparison().statusEqual() && report.comparison().bodyEqual();
                if (!ok) {
                    diff++;
                }
            }
        }
        return new ReplaySummary(total, diff);
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return null;
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static List<String> buildGoreplayArgs(Properties properties, String proxyBaseUrl) {
        List<String> args = new ArrayList<>();

        String rawArgs = System.getProperty("goreplay.args");
        if (rawArgs != null && !rawArgs.isBlank()) {
            args.addAll(splitArgs(rawArgs));
        }

        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith("goreplay.")) {
                continue;
            }
            if (name.equals("goreplay.args") || name.equals("goreplay.executable") || name.equals("goreplay.timeoutSeconds")) {
                continue;
            }

            String suffix = name.substring("goreplay.".length());
            if (suffix.isBlank()) {
                continue;
            }
            String value = properties.getProperty(name);
            args.addAll(toArgList(suffix, value));
        }

        args.removeIf(a -> a.startsWith("--output-http"));
        args.add("--output-http=" + proxyBaseUrl);
        args.sort(Comparator.comparingInt(a -> a.startsWith("--input") ? 0 : 1));
        return args;
    }

    private static List<String> toArgList(String suffix, String value) {
        String s = suffix.trim();
        if (s.startsWith("-")) {
            if (value == null || value.isBlank() || "true".equalsIgnoreCase(value)) {
                return List.of(s);
            }
            return List.of(s + "=" + value);
        }

        String kebab = toKebabCase(s.replace('.', '-'));
        String argName = "--" + kebab;
        if (value == null || value.isBlank() || "true".equalsIgnoreCase(value)) {
            return List.of(argName);
        }
        return List.of(argName + "=" + value);
    }

    private static String toKebabCase(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && prev != '-' && prev != '_') {
                    out.append('-');
                }
                out.append(Character.toLowerCase(c));
            } else if (c == '_') {
                out.append('-');
            } else {
                out.append(Character.toLowerCase(c));
            }
            prev = c;
        }
        return out.toString().replaceAll("-{2,}", "-");
    }

    private static List<String> splitArgs(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (!inSingle && !inDouble && Character.isWhitespace(c)) {
                if (!cur.isEmpty()) {
                    result.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }
            cur.append(c);
        }
        if (!cur.isEmpty()) {
            result.add(cur.toString());
        }
        return result;
    }

    private static Set<String> parseAllowMethods(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of("GET", "HEAD", "OPTIONS");
        }
        String[] parts = raw.split(",");
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String p : parts) {
            if (p == null || p.isBlank()) {
                continue;
            }
            out.add(p.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (out.isEmpty()) {
            return Set.of("GET", "HEAD", "OPTIONS");
        }
        return java.util.Set.copyOf(out);
    }

    private static String[] concat(String[] left, String[] right) {
        int l = left == null ? 0 : left.length;
        int r = right == null ? 0 : right.length;
        String[] out = new String[l + r];
        if (l > 0) {
            System.arraycopy(left, 0, out, 0, l);
        }
        if (r > 0) {
            System.arraycopy(right, 0, out, l, r);
        }
        return out;
    }

    private record ReplaySummary(long total, long diff) {
    }
}
