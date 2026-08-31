package cn.geelato.web.platform.srv.excel.exception;

/**
 * @author diabl
 * 文件不存在异常
 */
public class FileNotFoundException extends FileException {

    public static final int ERROR_CODE = 30015;

    public FileNotFoundException() {
        super(ERROR_CODE, "文件不存在");
    }

    public FileNotFoundException(String detailMessage) {
        super(ERROR_CODE, String.format("文件不存在：%s", detailMessage));
    }
}
