package cn.geelato.web.platform.srv.excel.exception;

/**
 * @author diabl
 * 文件内容为空异常
 */
public class FileContentIsEmptyException extends FileException {

    public static final int ERROR_CODE = 30017;

    public FileContentIsEmptyException() {
        super(ERROR_CODE, "文件内容为空");
    }

    public FileContentIsEmptyException(String detailMessage) {
        super(ERROR_CODE, String.format("文件内容为空：%s", detailMessage));
    }
}
