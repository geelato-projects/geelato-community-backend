package cn.geelato.it.support.replaydiff;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DiffReport(
        Instant timestamp,
        RequestInfo request,
        ResponseInfo baseline,
        ResponseInfo candidate,
        Comparison comparison
) {
    public record RequestInfo(
            String method,
            String uri,
            Map<String, List<String>> headers,
            String contentType,
            long originalBodyBytes,
            boolean truncated,
            String bodyText,
            String bodyBase64
    ) {
    }

    public record ResponseInfo(
            int status,
            Map<String, List<String>> headers,
            String contentType,
            long originalBodyBytes,
            boolean truncated,
            String bodyText,
            String bodyBase64,
            String sha256,
            boolean jsonBody
    ) {
    }

    public record Comparison(
            boolean statusEqual,
            boolean bodyEqual,
            boolean jsonComparable,
            List<JsonDiffEntry> jsonDiffs
    ) {
    }
}
