package cn.geelato.ide.enums;

/**
 * IDE 同步操作类型常量。
 *
 * @author geelato
 */
public final class IdeSyncAction {
    /** 文件→DB（插件 push 或 IDE 端写入） */
    public static final String PULL = "PULL";
    /** DB→文件（插件 pull） */
    public static final String PUSH = "PUSH";
    /** dry-run 校验 */
    public static final String DRYRUN = "DRYRUN";
    /** 新建脚本 */
    public static final String CREATE = "CREATE";
    /** 更新脚本 */
    public static final String UPDATE = "UPDATE";
    /** 删除脚本 */
    public static final String DELETE = "DELETE";
    /** 发布脚本 */
    public static final String PUBLISH = "PUBLISH";

    private IdeSyncAction() {
    }
}
