package cn.geelato.web.platform.srv.excel.exception;

import cn.geelato.web.platform.exception.PlatformErrorCodes;

/**
 * @author diabl
 * 12.8 文件内容读取失败异常
 */
public class FileContentReadFailedException extends FileException {

    public FileContentReadFailedException() {
        super(PlatformErrorCodes.FILE_CONTENT_READ_FAILED);
    }

    public FileContentReadFailedException(String detailMessage) {
        super(PlatformErrorCodes.FILE_CONTENT_READ_FAILED, String.format("%s：%s", PlatformErrorCodes.FILE_CONTENT_READ_FAILED.getDefaultMessage(), detailMessage));
    }
}
