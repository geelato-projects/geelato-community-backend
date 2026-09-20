package cn.geelato.archive.enums;

/**
 * 归档执行模式（仅 MYSQL 目标）。
 * <ul>
 *   <li>{@link #AUTO}：运行时探测——同实例→SERVER；localInfile 可用→LOCAL_FILE；否则 JDBC；</li>
 *   <li>{@link #SERVER}：{@code INSERT INTO 目标 SELECT …} + DELETE 服务端完成，数据零出库零 JVM，性能最高（限同库/同实例）；</li>
 *   <li>{@link #LOCAL_FILE}：源库流式导出应用侧临时文件 → 归档库 LOAD DATA LOCAL INFILE，跨实例推荐；</li>
 *   <li>{@link #JDBC}：流式 fetch + rewriteBatchedStatements 批量，通用兜底。</li>
 * </ul>
 */
public enum ExecutionModeEnum {

    AUTO("自动探测"),
    SERVER("服务端搬移（INSERT SELECT）"),
    LOCAL_FILE("临时文件（LOAD DATA LOCAL INFILE）"),
    JDBC("JDBC 批量管道");

    private final String description;

    ExecutionModeEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static ExecutionModeEnum parse(String value) {
        for (ExecutionModeEnum e : values()) {
            if (e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        return null;
    }
}
