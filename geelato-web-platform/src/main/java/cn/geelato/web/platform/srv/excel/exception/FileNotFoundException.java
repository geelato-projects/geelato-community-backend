package cn.geelato.web.platform.srv.excel.exception;

import cn.geelato.web.platform.exception.PlatformErrorCodes;

/**
 * @author diabl
 * 12.5 文件不存在异常
 */
public class FileNotFoundException extends FileException {

    public FileNotFoundException() {
        super(PlatformErrorCodes.FILE_NOT_FOUND);
    }

    public FileNotFoundException(String detailMessage) {
        super(PlatformErrorCodes.FILE_NOT_FOUND, String.format("%s：%s", PlatformErrorCodes.FILE_NOT_FOUND.getDefaultMessage(), detailMessage));
    }
}
