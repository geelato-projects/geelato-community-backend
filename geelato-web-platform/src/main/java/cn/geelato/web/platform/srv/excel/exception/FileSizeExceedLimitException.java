package cn.geelato.web.platform.srv.excel.exception;

/**
 * @author diabl
 * 文件大小超出限制异常
 */
public class FileSizeExceedLimitException extends FileException {

    public static final int ERROR_CODE = 30014;

    public FileSizeExceedLimitException() {
        super(ERROR_CODE, "文件超出大小限制");
    }

    public FileSizeExceedLimitException(String detailMessage) {
        super(ERROR_CODE, String.format("文件超出大小限制：%s", detailMessage));
    }
}
