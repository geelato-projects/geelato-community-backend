package cn.geelato.web.platform.srv.excel.exception;

import cn.geelato.web.platform.exception.PlatformErrorCodes;

/**
 * @author diabl
 * 12.3 文件类型不支持异常
 */
public class FileTypeNotSupportedException extends FileException {

    public FileTypeNotSupportedException() {
        super(PlatformErrorCodes.FILE_TYPE_NOT_SUPPORTED);
    }

    public FileTypeNotSupportedException(String detailMessage) {
        super(PlatformErrorCodes.FILE_TYPE_NOT_SUPPORTED, String.format("%s：%s", PlatformErrorCodes.FILE_TYPE_NOT_SUPPORTED.getDefaultMessage(), detailMessage));
    }
}
