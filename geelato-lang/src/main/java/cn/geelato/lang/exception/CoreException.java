package cn.geelato.lang.exception;


public abstract class CoreException extends RuntimeException {
    private final int errorCode;

    public CoreException(int errorCode, String msg) {
        this(errorCode, msg, null);
    }

    public CoreException(int errorCode, String msg, Throwable cause) {
        super(msg, cause);
        this.errorCode = errorCode;
    }

    /** 业务错误码。 */
    public int getErrorCode() {
        return errorCode;
    }

    /** 错误文案，等价于 {@link #getMessage()}（保留历史 API）。 */
    public String getErrorMsg() {
        return getMessage();
    }

    /** 对应 HTTP 响应状态码，默认 500；鉴权类子类按语义覆写（401/403/400）。 */
    public int getHttpStatus() {
        return 500;
    }

    /** 在线文档 slug，默认 null（docUrl 指向错误码参考页锚点）；子类按需覆写。 */
    public String getDocSlug() {
        return null;
    }

    /**
     * 返回给前端的用户可见文案。
     * <p>默认与 {@link #getErrorMsg()} 一致（业务异常的文案本身就是面向用户的，如"验证码错误"）；
     * 技术类子类（如 SQL 执行异常）可覆写本方法，将 SQL 语句、参数等技术详情屏蔽在服务端日志中，
     * 避免原始错误信息直接暴露给最终用户。</p>
     */
    public String getUserMessage() {
        return getErrorMsg();
    }

    /**
     * 该类异常是否需要记录服务端错误日志并落库（platform_exception_log）。
     * <p>默认 true。属于常规业务事件、非服务端错误的异常（如鉴权不通过、令牌过期、需选择租户），
     * 由异常类的定义者覆写为 false：全局异常处理器将跳过 log.error 与异常落库，
     * 响应也不再生成反馈凭据（logTag）——没有日志可关联，凭据无意义。</p>
     */
    public boolean shouldLog() {
        return true;
    }
}
