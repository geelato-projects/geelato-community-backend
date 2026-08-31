package cn.geelato.web.platform.srv.excel.exception;

import cn.geelato.lang.exception.CoreException;

/**
 * 文件异常根类。文件相关异常码统一归在 30xxx 段（10xxx 数据解析 / 20xxx 认证 / 30xxx 文件 / 40xxx 插件 / 50xxx 系统通用）。
 *
 * @author diabl
 */
public class FileException extends CoreException {

    public static final int ERROR_CODE = 30000;

    public FileException() {
        super(ERROR_CODE, "文件处理失败");
    }

    /** 供文件族二级子类以其自身错误码构造（错误码定义在子类中）。 */
    public FileException(int errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 以详细信息构造，使用根文件错误码，文案格式为 "文件处理失败：{详细信息}"。
     */
    public FileException(String detailMessage) {
        super(ERROR_CODE, String.format("文件处理失败：%s", detailMessage));
    }
}
