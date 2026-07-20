package cn.geelato.web.platform.srv.excel.exception;

import cn.geelato.web.platform.exception.PlatformErrorCodes;

/**
 * @author diabl
 * 12.6 文件内容校验失败异常
 */
public class FileContentValidFailedException extends FileException {

    public FileContentValidFailedException() {
        super(PlatformErrorCodes.FILE_CONTENT_VALID_FAILED);
    }

    public FileContentValidFailedException(String detailMessage) {
        super(PlatformErrorCodes.FILE_CONTENT_VALID_FAILED, String.format("%s：%s", PlatformErrorCodes.FILE_CONTENT_VALID_FAILED.getDefaultMessage(), detailMessage));
    }
}
