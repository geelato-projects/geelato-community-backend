package cn.geelato.lang.api;

import lombok.Getter;

/**
 * @author diabl
 */

@Getter
public enum ResultCode {
    // todo，为了暂时不影响前端，先保留原先定义20000和-2的状态码
    RC200(20000, "ok", "success"),

    RC403(403, "fail", "not authorized"),

    RC500(-2, "fail", "fail");

    private final int code;
    private final String status;
    private final String message;

    ResultCode(int code, String status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
