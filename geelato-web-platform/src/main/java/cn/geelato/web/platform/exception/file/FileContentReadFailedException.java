package cn.geelato.web.platform.exception.file;

/**
 * @author diabl
 * @description: 12.8 文件内容读取失败异常
 * @date 2023/10/25 17:31
 */
public class FileContentReadFailedException extends FileException {
    private static final String MESSAGE = "12.8 File Content Read Failed Exception";
    private static final int CODE = 1218;

    public FileContentReadFailedException() {
        super(MESSAGE, CODE);
    }

    public FileContentReadFailedException(String msg, int code) {
        super(msg, code);
    }

    public FileContentReadFailedException(String detailMessage) {
        super(String.format("%s：%s", MESSAGE, detailMessage), CODE);
    }
}
