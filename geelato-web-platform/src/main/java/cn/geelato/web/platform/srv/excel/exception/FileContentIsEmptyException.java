package cn.geelato.web.platform.srv.excel.exception;

/**
 * @author diabl
 * 12.7 文件内容为空异常
 */
public class FileContentIsEmptyException extends FileException {
    private static final String MESSAGE = "12.7 File Content Is Empty Exception";
    private static final int CODE = 1217;

    public FileContentIsEmptyException() {
        super(MESSAGE, CODE);
    }

    public FileContentIsEmptyException(String msg, int code) {
        super(msg, code);
    }

    public FileContentIsEmptyException(String detailMessage) {
        super(String.format("%s：%s", MESSAGE, detailMessage), CODE);
    }
}
