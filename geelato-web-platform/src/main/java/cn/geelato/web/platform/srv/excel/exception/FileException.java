package cn.geelato.web.platform.srv.excel.exception;


import cn.geelato.lang.exception.CoreException;
import cn.geelato.lang.exception.ErrorCode;
import cn.geelato.web.platform.exception.PlatformErrorCodes;

/**
 * 文件异常根类。文件相关异常码统一归在 12xx 段。
 *
 * @author diabl
 */
public class FileException extends CoreException {

    public FileException() {
        super(PlatformErrorCodes.FILE);
    }

    public FileException(ErrorCode ec) {
        super(ec);
    }

    public FileException(ErrorCode ec, String detailMessage) {
        super(ec, detailMessage);
    }

    /**
     * 以详细信息构造，使用根文件错误码（{@link PlatformErrorCodes#FILE}），
     * 文案格式为 "{默认文案}：{详细信息}"。
     */
    public FileException(String detailMessage) {
        super(PlatformErrorCodes.FILE, String.format("%s：%s", PlatformErrorCodes.FILE.getDefaultMessage(), detailMessage));
    }
}
