package cn.geelato.web.platform.srv.excel.exception;

import cn.geelato.web.platform.exception.PlatformErrorCodes;

/**
 * @author diabl
 * 12.7 文件内容为空异常
 */
public class FileContentIsEmptyException extends FileException {

    public FileContentIsEmptyException() {
        super(PlatformErrorCodes.FILE_CONTENT_IS_EMPTY);
    }

    public FileContentIsEmptyException(String detailMessage) {
        super(PlatformErrorCodes.FILE_CONTENT_IS_EMPTY, String.format("%s：%s", PlatformErrorCodes.FILE_CONTENT_IS_EMPTY.getDefaultMessage(), detailMessage));
    }
}
