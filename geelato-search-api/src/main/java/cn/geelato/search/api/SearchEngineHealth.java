package cn.geelato.search.api;

/**
 * 引擎健康状态。
 */
public class SearchEngineHealth {

    public enum Status {
        UP, DEGRADED, DOWN
    }

    private final Status status;
    private final String detail;

    public SearchEngineHealth(Status status, String detail) {
        this.status = status;
        this.detail = detail;
    }

    public static SearchEngineHealth up(String detail) {
        return new SearchEngineHealth(Status.UP, detail);
    }

    public static SearchEngineHealth down(String detail) {
        return new SearchEngineHealth(Status.DOWN, detail);
    }

    public Status getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }
}
