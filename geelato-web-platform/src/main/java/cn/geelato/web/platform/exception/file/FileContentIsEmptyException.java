package cn.geelato.web.platform.exception.file;

/**
 * @author diabl
 * @description: 12.7 文件内容为空异常
 * @date 2023/10/25 16:21
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
