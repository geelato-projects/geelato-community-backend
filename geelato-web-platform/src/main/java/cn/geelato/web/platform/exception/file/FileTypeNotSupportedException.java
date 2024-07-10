package cn.geelato.web.platform.exception.file;

/**
 * @author diabl
 * @description: 12.3 文件类型不支持异常
 * @date 2023/10/25 16:20
 */
public class FileTypeNotSupportedException extends FileException {
    private static final String MESSAGE = "12.3 File Type Not Support Exception";
    private static final int CODE = 1213;

    public FileTypeNotSupportedException() {
        super(MESSAGE, CODE);
    }

    public FileTypeNotSupportedException(String msg, int code) {
        super(msg, code);
    }

    public FileTypeNotSupportedException(String detailMessage) {
        super(String.format("%s：%s", MESSAGE, detailMessage), CODE);
    }
}
