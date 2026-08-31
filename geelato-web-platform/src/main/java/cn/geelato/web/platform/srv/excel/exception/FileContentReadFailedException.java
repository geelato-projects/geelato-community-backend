package cn.geelato.web.platform.srv.excel.exception;

/**
 * @author diabl
 * 文件内容读取失败异常
 */
public class FileContentReadFailedException extends FileException {

    public static final int ERROR_CODE = 30018;

    public FileContentReadFailedException() {
        super(ERROR_CODE, "文件内容读取失败");
    }

    public FileContentReadFailedException(String detailMessage) {
        super(ERROR_CODE, String.format("文件内容读取失败：%s", detailMessage));
    }
}
