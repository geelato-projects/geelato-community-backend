package cn.geelato.archive.enums;

/**
 * 归档执行器类型。
 * <p>一期固定 {@link #INLINE}（内置通道，装上即用）；SEATUNNEL 等外部执行器为二期预留
 * （量大后数据面移交专业同步引擎，平台只做策略/编排/对账）。</p>
 */
public enum ExecutorTypeEnum {

    INLINE("内置通道"),
    SEATUNNEL("SeaTunnel 外部执行器（二期）");

    private final String description;

    ExecutorTypeEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
