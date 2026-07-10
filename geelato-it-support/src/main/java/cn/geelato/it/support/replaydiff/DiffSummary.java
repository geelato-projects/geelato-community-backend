package cn.geelato.it.support.replaydiff;

import java.util.List;

public record DiffSummary(
        long totalRequests,
        long diffRequests,
        long statusMismatchRequests,
        long bodyMismatchRequests,
        List<TopPathCount> topJsonDiffPaths
) {
    public record TopPathCount(String path, long count) {
    }
}

