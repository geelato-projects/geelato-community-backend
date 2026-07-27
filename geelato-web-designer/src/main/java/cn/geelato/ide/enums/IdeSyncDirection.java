package cn.geelato.ide.enums;

/**
 * IDE 同步方向常量。
 *
 * @author geelato
 */
public final class IdeSyncDirection {
    /** 本地文件 → 数据库 */
    public static final String FILE_TO_DB = "FILE_TO_DB";
    /** 数据库 → 本地文件 */
    public static final String DB_TO_FILE = "DB_TO_FILE";

    private IdeSyncDirection() {
    }
}
