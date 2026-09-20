package cn.geelato.archive.exception;

import cn.geelato.lang.exception.CoreException;

/**
 * 数据归档异常（70xxx 段）。
 * <p>码表登记：website/official-docs/zh-cn/reference/error-codes.md（码值是前后端契约）。</p>
 * <ul>
 *   <li>70001 策略校验失败（源表/列不存在、归档库不可达、WHERE 语法或安全校验不通过、目标类型不支持等）；</li>
 *   <li>70002 归档目标表结构漂移（列结构不一致，绝不静默跳列）；</li>
 *   <li>70003 归档目标写入对账不一致（源库未动，零丢失）；</li>
 *   <li>70004 源表删除对账不一致（目标已完整，可安全重跑）；</li>
 *   <li>70005 策略并发冲突（同策略已有运行中的 run）。</li>
 * </ul>
 */
public class ArchiveException extends CoreException {

    public static final int VALIDATION_ERROR = 70001;
    public static final int SCHEMA_DRIFT = 70002;
    public static final int TARGET_WRITE_MISMATCH = 70003;
    public static final int SOURCE_DELETE_MISMATCH = 70004;
    public static final int POLICY_RUNNING_CONFLICT = 70005;

    public ArchiveException(int errorCode, String msg) {
        super(errorCode, msg);
    }

    public ArchiveException(int errorCode, String msg, Throwable cause) {
        super(errorCode, msg, cause);
    }

    public static ArchiveException validationError(String msg) {
        return new ArchiveException(VALIDATION_ERROR, msg);
    }

    public static ArchiveException validationError(String msg, Throwable cause) {
        return new ArchiveException(VALIDATION_ERROR, msg, cause);
    }

    public static ArchiveException schemaDrift(String msg) {
        return new ArchiveException(SCHEMA_DRIFT, msg);
    }

    public static ArchiveException targetWriteMismatch(String msg) {
        return new ArchiveException(TARGET_WRITE_MISMATCH, msg);
    }

    public static ArchiveException sourceDeleteMismatch(String msg) {
        return new ArchiveException(SOURCE_DELETE_MISMATCH, msg);
    }

    public static ArchiveException policyRunningConflict(String msg) {
        return new ArchiveException(POLICY_RUNNING_CONFLICT, msg);
    }
}
