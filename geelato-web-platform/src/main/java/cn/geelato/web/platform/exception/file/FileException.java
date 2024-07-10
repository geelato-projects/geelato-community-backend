package cn.geelato.web.platform.exception.file;

import cn.geelato.core.exception.CoreException;

/**
 * @author diabl
 * @date 2023/10/25 11:28
 */
public class FileException extends CoreException {
    private static final String MESSAGE = "12 File Exception";
    private static final int CODE = 1200;

    public FileException() {
        super(CODE,MESSAGE);
    }

    public FileException(String msg, int code) {
        super( code, msg);
    }

    public FileException(String detailMessage) {
        super(CODE,String.format("%s：%s", MESSAGE, detailMessage));
    }
}
