package cn.geelato.web.platform.srv.excel.exception;

/**
 * @author diabl
 * 文件内容校验失败异常
 */
public class FileContentValidFailedException extends FileException {

    public static final int ERROR_CODE = 30016;

    public FileContentValidFailedException() {
        super(ERROR_CODE, "文件内容校验失败");
    }

    public FileContentValidFailedException(String detailMessage) {
        super(ERROR_CODE, String.format("文件内容校验失败：%s", detailMessage));
    }
}
