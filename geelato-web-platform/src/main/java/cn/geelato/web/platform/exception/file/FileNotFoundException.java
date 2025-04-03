package cn.geelato.web.platform.exception.file;

/**
 * @author diabl
 * 12.5 文件不存在异常
 */
public class FileNotFoundException extends FileException {
    private static final String MESSAGE = "12.5 File Not Found Exception";
    private static final int CODE = 1215;

    public FileNotFoundException() {
        super(MESSAGE, CODE);
    }

    public FileNotFoundException(String msg, int code) {
        super(msg, code);
    }

    public FileNotFoundException(String detailMessage) {
        super(String.format("%s：%s", MESSAGE, detailMessage), CODE);
    }
}
