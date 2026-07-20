package cn.geelato.web.platform.srv.excel.exception;

import cn.geelato.web.platform.exception.PlatformErrorCodes;

/**
 * @author diabl
 * 12.4 文件大小超出限制异常
 */
public class FileSizeExceedLimitException extends FileException {

    public FileSizeExceedLimitException() {
        super(PlatformErrorCodes.FILE_SIZE_EXCEED_LIMIT);
    }

    public FileSizeExceedLimitException(String detailMessage) {
        super(PlatformErrorCodes.FILE_SIZE_EXCEED_LIMIT, String.format("%s：%s", PlatformErrorCodes.FILE_SIZE_EXCEED_LIMIT.getDefaultMessage(), detailMessage));
    }
}
