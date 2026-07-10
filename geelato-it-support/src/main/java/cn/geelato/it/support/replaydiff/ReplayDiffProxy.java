package cn.geelato.it.support.replaydiff;

import cn.geelato.it.support.json.ObjectMappers;
import cn.geelato.it.support.util.Hashing;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

public final class ReplayDiffProxy implements AutoCloseable {
    private final ReplayDiffProxyConfig config;
    private final HttpClient client;
    private final JsonlDiffReportWriter reportWriter;
    private final ExecutorService executor;
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder diffRequests = new LongAdder();
    private final LongAdder statusMismatchRequests = new LongAdder();
    private final LongAdder bodyMismatchRequests = new LongAdder();
    private final ConcurrentHashMap<String, LongAdder> topJsonDiffPaths = new ConcurrentHashMap<>();

    private volatile HttpServer server;

    public ReplayDiffProxy(ReplayDiffProxyConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        this.reportWriter = new JsonlDiffReportWriter(config.reportFile());
        this.executor = Executors.newCachedThreadPool();
        if (config.baselineHostAllowList() != null && !config.baselineHostAllowList().isEmpty()) {
            String host = config.baselineBaseUri() == null ? null : config.baselineBaseUri().getHost();
            if (host == null || !config.baselineHostAllowList().contains(host)) {
                throw new IllegalArgumentException("baseline host not in allowlist: " + host);
            }
        }
    }

    public void start() {
        if (server != null) {
            return;
        }
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress(config.listenHost(), config.listenPort()), 0);
            s.createContext("/", new Handler());
            s.setExecutor(executor);
            s.start();
            server = s;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start server", e);
        }
    }

    public int port() {
        HttpServer s = server;
        if (s == null) {
            return config.listenPort();
        }
        return s.getAddress().getPort();
    }

    @Override
    public void close() {
        HttpServer s = server;
        if (s != null) {
            s.stop(0);
            server = null;
        }
        executor.shutdownNow();
        reportWriter.close();
        writeSummary();
    }

    private final class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if (!config.allowedMethods().contains(method.toUpperCase(Locale.ROOT))) {
                DiffReport report = buildBlockedReport(exchange, method);
                reportWriter.write(report);
                recordSummary(report);
                byte[] msg = ("Method not allowed: " + method).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(405, msg.length);
                exchange.getResponseBody().write(msg);
                exchange.close();
                return;
            }

            byte[] requestBody = readAllBytes(exchange.getRequestBody());
            URI requestUri = exchange.getRequestURI();
            Map<String, List<String>> requestHeaders = HeaderSanitizer.sanitize(exchange.getRequestHeaders());
            String requestContentType = firstHeader(requestHeaders, "content-type");

            URI baselineUri = resolve(config.baselineBaseUri(), requestUri);
            URI candidateUri = resolve(config.candidateBaseUri(), requestUri);

            HttpRequest baselineReq = buildUpstreamRequest(exchange, baselineUri, method, requestBody);
            HttpRequest candidateReq = buildUpstreamRequest(exchange, candidateUri, method, requestBody);

            CompletableFuture<HttpResponse<byte[]>> baselineFuture = client.sendAsync(baselineReq, HttpResponse.BodyHandlers.ofByteArray());
            CompletableFuture<HttpResponse<byte[]>> candidateFuture = client.sendAsync(candidateReq, HttpResponse.BodyHandlers.ofByteArray());

            HttpResponse<byte[]> baselineResp;
            HttpResponse<byte[]> candidateResp;
            try {
                baselineResp = baselineFuture.join();
                candidateResp = candidateFuture.join();
            } catch (Exception e) {
                byte[] msg = ("Upstream error: " + e.getMessage()).getBytes();
                exchange.sendResponseHeaders(502, msg.length);
                exchange.getResponseBody().write(msg);
                exchange.close();
                return;
            }

            DiffReport report = buildReport(method, requestUri, requestHeaders, requestContentType, requestBody, baselineResp, candidateResp);
            reportWriter.write(report);
            recordSummary(report);

            HttpResponse<byte[]> selected = config.responseMode() == ProxyResponseMode.BASELINE ? baselineResp : candidateResp;
            writeDownstreamResponse(exchange, selected);
        }
    }

    private void recordSummary(DiffReport report) {
        totalRequests.increment();
        DiffReport.Comparison c = report == null ? null : report.comparison();
        if (c == null) {
            diffRequests.increment();
            return;
        }
        if (!c.statusEqual()) {
            statusMismatchRequests.increment();
            diffRequests.increment();
        }
        if (!c.bodyEqual()) {
            bodyMismatchRequests.increment();
            diffRequests.increment();
        }
        if (c.jsonDiffs() != null) {
            for (JsonDiffEntry e : c.jsonDiffs()) {
                if (e == null || e.path() == null || e.path().isBlank()) {
                    continue;
                }
                topJsonDiffPaths.computeIfAbsent(e.path(), k -> new LongAdder()).increment();
            }
        }
    }

    private void writeSummary() {
        try {
            List<DiffSummary.TopPathCount> top = topJsonDiffPaths.entrySet().stream()
                    .map(e -> new DiffSummary.TopPathCount(e.getKey(), e.getValue().sum()))
                    .sorted((a, b) -> Long.compare(b.count(), a.count()))
                    .limit(50)
                    .toList();
            DiffSummary summary = new DiffSummary(
                    totalRequests.sum(),
                    diffRequests.sum(),
                    statusMismatchRequests.sum(),
                    bodyMismatchRequests.sum(),
                    top
            );
            var mapper = ObjectMappers.defaultMapper();
            var summaryFile = config.reportFile().getParent().resolve("summary.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(summaryFile.toFile(), summary);
        } catch (Exception ignored) {
        }
    }

    private DiffReport buildReport(
            String method,
            URI requestUri,
            Map<String, List<String>> requestHeaders,
            String requestContentType,
            byte[] requestBody,
            HttpResponse<byte[]> baselineResp,
            HttpResponse<byte[]> candidateResp
    ) {
        byte[] baselineBody = baselineResp.body() == null ? new byte[0] : baselineResp.body();
        byte[] candidateBody = candidateResp.body() == null ? new byte[0] : candidateResp.body();

        String baselineContentType = firstHeader(baselineResp.headers().map(), "content-type");
        String candidateContentType = firstHeader(candidateResp.headers().map(), "content-type");

        boolean baselineJson = isJsonContentType(baselineContentType) || looksLikeJson(baselineBody);
        boolean candidateJson = isJsonContentType(candidateContentType) || looksLikeJson(candidateBody);

        boolean jsonComparable = baselineJson && candidateJson;
        boolean bodyEqual;
        List<JsonDiffEntry> jsonDiffs = List.of();

        if (jsonComparable) {
            JsonNode left = tryParseJson(baselineBody);
            JsonNode right = tryParseJson(candidateBody);
            if (left != null && right != null) {
                JsonNode leftPruned = JsonDiffUtil.prune(left, config.ignoreConfig());
                JsonNode rightPruned = JsonDiffUtil.prune(right, config.ignoreConfig());
                bodyEqual = leftPruned.equals(rightPruned);
                if (!bodyEqual) {
                    jsonDiffs = JsonDiffUtil.diff(left, right, config.ignoreConfig(), config.maxJsonDiffEntries());
                }
            } else {
                jsonComparable = false;
                bodyEqual = Arrays.equals(baselineBody, candidateBody);
            }
        } else {
            bodyEqual = Arrays.equals(baselineBody, candidateBody);
        }

        boolean statusEqual = baselineResp.statusCode() == candidateResp.statusCode();

        CapturedBody requestCaptured = captureBody(requestBody, requestContentType);

        DiffReport.RequestInfo requestInfo = new DiffReport.RequestInfo(
                method,
                requestUri.toString(),
                requestHeaders,
                requestContentType,
                requestBody.length,
                requestCaptured.truncated(),
                requestCaptured.bodyText(),
                requestCaptured.bodyBase64()
        );

        Map<String, List<String>> baselineHeaders = HeaderSanitizer.sanitize(baselineResp.headers().map());
        CapturedBody baselineCaptured = captureBody(baselineBody, baselineContentType);
        DiffReport.ResponseInfo baselineInfo = new DiffReport.ResponseInfo(
                baselineResp.statusCode(),
                baselineHeaders,
                baselineContentType,
                baselineBody.length,
                baselineCaptured.truncated(),
                baselineCaptured.bodyText(),
                baselineCaptured.bodyBase64(),
                Hashing.sha256Hex(baselineBody),
                baselineJson
        );

        Map<String, List<String>> candidateHeaders = HeaderSanitizer.sanitize(candidateResp.headers().map());
        CapturedBody candidateCaptured = captureBody(candidateBody, candidateContentType);
        DiffReport.ResponseInfo candidateInfo = new DiffReport.ResponseInfo(
                candidateResp.statusCode(),
                candidateHeaders,
                candidateContentType,
                candidateBody.length,
                candidateCaptured.truncated(),
                candidateCaptured.bodyText(),
                candidateCaptured.bodyBase64(),
                Hashing.sha256Hex(candidateBody),
                candidateJson
        );

        DiffReport.Comparison comparison = new DiffReport.Comparison(statusEqual, bodyEqual, jsonComparable, jsonDiffs);
        return new DiffReport(Instant.now(), requestInfo, baselineInfo, candidateInfo, comparison);
    }

    private DiffReport buildBlockedReport(HttpExchange exchange, String method) throws IOException {
        byte[] requestBody = readAllBytes(exchange.getRequestBody());
        Map<String, List<String>> requestHeaders = HeaderSanitizer.sanitize(exchange.getRequestHeaders());
        String requestContentType = firstHeader(requestHeaders, "content-type");
        CapturedBody requestCaptured = captureBody(requestBody, requestContentType);
        DiffReport.RequestInfo requestInfo = new DiffReport.RequestInfo(
                method,
                exchange.getRequestURI() == null ? null : exchange.getRequestURI().toString(),
                requestHeaders,
                requestContentType,
                requestBody.length,
                requestCaptured.truncated(),
                requestCaptured.bodyText(),
                requestCaptured.bodyBase64()
        );
        return new DiffReport(Instant.now(), requestInfo, null, null, null);
    }

    private record CapturedBody(boolean truncated, String bodyText, String bodyBase64) {
    }

    private CapturedBody captureBody(byte[] body, String contentType) {
        BodyCaptureConfig cap = config.bodyCaptureConfig();
        if (cap == null || !cap.enabled() || body == null) {
            return new CapturedBody(false, null, null);
        }
        byte[] bytes = body;
        boolean truncated = false;
        if (bytes.length > cap.maxBytes()) {
            bytes = Arrays.copyOf(bytes, cap.maxBytes());
            truncated = true;
        }
        boolean asText = cap.shouldTreatAsText(contentType) || isJsonContentType(contentType) || looksLikeJson(bytes);
        if (asText) {
            return new CapturedBody(truncated, new String(bytes, StandardCharsets.UTF_8), null);
        }
        return new CapturedBody(truncated, null, Base64.getEncoder().encodeToString(bytes));
    }

    private void writeDownstreamResponse(HttpExchange exchange, HttpResponse<byte[]> response) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.clear();
        for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {
            String key = entry.getKey();
            if (shouldSkipDownstreamHeader(key)) {
                continue;
            }
            headers.put(key, new ArrayList<>(entry.getValue()));
        }
        byte[] body = response.body() == null ? new byte[0] : response.body();
        exchange.sendResponseHeaders(response.statusCode(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private HttpRequest buildUpstreamRequest(HttpExchange exchange, URI uri, String method, byte[] body) {
        HttpRequest.BodyPublisher publisher = body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).method(method, publisher);

        Headers reqHeaders = exchange.getRequestHeaders();
        for (Map.Entry<String, List<String>> entry : reqHeaders.entrySet()) {
            String name = entry.getKey();
            if (shouldSkipUpstreamHeader(name)) {
                continue;
            }
            for (String value : entry.getValue()) {
                builder.header(name, value);
            }
        }
        return builder.build();
    }

    private static boolean shouldSkipUpstreamHeader(String headerName) {
        if (headerName == null) {
            return true;
        }
        String h = headerName.toLowerCase(Locale.ROOT);
        return Set.of("host", "content-length", "connection").contains(h);
    }

    private static boolean shouldSkipDownstreamHeader(String headerName) {
        if (headerName == null) {
            return true;
        }
        String h = headerName.toLowerCase(Locale.ROOT);
        return Set.of("transfer-encoding", "content-length", "connection", "keep-alive").contains(h);
    }

    private static URI resolve(URI baseUri, URI requestUri) {
        String base = baseUri.toString();
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        String relative = requestUri.toString();
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return URI.create(base).resolve(relative);
    }

    private static String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null || name == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                List<String> values = entry.getValue();
                if (values == null || values.isEmpty()) {
                    return null;
                }
                return values.get(0);
            }
        }
        return null;
    }

    private static boolean isJsonContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.contains("application/json") || ct.contains("+json");
    }

    private static boolean looksLikeJson(byte[] body) {
        if (body == null || body.length == 0) {
            return false;
        }
        int i = 0;
        while (i < body.length && (body[i] == ' ' || body[i] == '\n' || body[i] == '\r' || body[i] == '\t')) {
            i++;
        }
        if (i >= body.length) {
            return false;
        }
        return body[i] == '{' || body[i] == '[';
    }

    private static JsonNode tryParseJson(byte[] body) {
        try {
            return ObjectMappers.defaultMapper().readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        return in.readAllBytes();
    }
}
