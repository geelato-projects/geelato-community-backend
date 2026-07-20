package cn.geelato.lang.exception;


import lombok.Getter;

/**
 * 平台异常抽象基类。
 * <p>持有 {@link ErrorCode} 作为错误码元数据的唯一事实源（业务码值、默认文案、HTTP 状态码、文档 slug）。
 * 同时保留 {@link #errorCode}（int）与 {@link #errorMsg}（String）字段以向后兼容已有调用方。</p>
 *
 * <h3>构造方式</h3>
 * <ul>
 *   <li>推荐：以 {@link ErrorCode} 构造（{@link #CoreException(ErrorCode)} /
 *       {@link #CoreException(ErrorCode, String)} /
 *       {@link #CoreException(ErrorCode, String, Throwable)}）。</li>
 *   <li>兼容：以 {@code (int code, String msg)} 构造，内部包装为匿名 {@link ErrorCode}，
 *       供尚未枚举化的历史子类过渡使用。</li>
 * </ul>
 *
 * @author geelato
 */
@Getter
public abstract class CoreException extends RuntimeException {
    private final String errorMsg;
    private final int errorCode;
    private final ErrorCode error;

    /**
     * 以 {@link ErrorCode} 构造，文案使用 errorCode 的默认文案。
     */
    public CoreException(ErrorCode ec) {
        this(ec, ec.getDefaultMessage(), null);
    }

    /**
     * 以 {@link ErrorCode} 构造，自定义文案。
     */
    public CoreException(ErrorCode ec, String msg) {
        this(ec, msg, null);
    }

    /**
     * 以 {@link ErrorCode} 构造，自定义文案与异常链。
     */
    public CoreException(ErrorCode ec, String msg, Throwable cause) {
        super(msg, cause);
        this.error = ec;
        this.errorMsg = msg != null ? msg : ec.getDefaultMessage();
        this.errorCode = ec.getCode();
    }

    /**
     * 兼容构造器：以裸 int code 与文案构造。
     * <p>内部包装为匿名 {@link ErrorCode}（默认 HTTP 500、无 docSlug），
     * 供尚未枚举化的历史子类过渡使用，新代码不应使用。</p>
     */
    public CoreException(int code, String msg) {
        this(code, msg, null);
    }

    /**
     * 兼容构造器：以裸 int code、文案与异常链构造。
     */
    public CoreException(int code, String msg, Throwable cause) {
        super(msg, cause);
        this.errorMsg = msg;
        this.errorCode = code;
        this.error = new ErrorCode() {
            @Override
            public int getCode() {
                return code;
            }

            @Override
            public String getDefaultMessage() {
                return msg;
            }
        };
    }
}
