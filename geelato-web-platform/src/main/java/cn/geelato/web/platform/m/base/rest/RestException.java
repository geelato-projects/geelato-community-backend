package cn.geelato.web.platform.m.base.rest;

import org.springframework.http.HttpStatus;

/**
 * 专用于Restful Service的异常.
 */
public class RestException extends RuntimeException {

    public HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

    public RestException() {
    }

    public RestException(HttpStatus status) {
        this.status = status;
    }

    public RestException(String message) {
        super(message);
    }

    public RestException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
