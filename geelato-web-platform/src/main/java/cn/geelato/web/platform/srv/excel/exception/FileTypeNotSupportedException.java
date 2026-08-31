package cn.geelato.web.platform.srv.excel.exception;

/**
 * @author diabl
 * 文件类型不支持异常
 */
public class FileTypeNotSupportedException extends FileException {

    public static final int ERROR_CODE = 30013;

    public FileTypeNotSupportedException() {
        super(ERROR_CODE, "文件类型不支持");
    }

    public FileTypeNotSupportedException(String detailMessage) {
        super(ERROR_CODE, String.format("文件类型不支持：%s", detailMessage));
    }
}
