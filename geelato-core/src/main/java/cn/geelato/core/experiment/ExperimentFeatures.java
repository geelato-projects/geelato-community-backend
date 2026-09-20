package cn.geelato.core.experiment;

import java.util.Set;

/**
 * 实验功能登记表：功能名常量与构建期白名单。
 *
 * <p>白名单是代码常量，编译进 jar 后运行时不可变——某个实验特性是否允许开启
 * 在打包成 jar 时即已决定，运行时（application.properties / 配置中心）不可更改。
 * 新增实验特性须在此登记常量并加入 {@link #allowed()}，重新打包后生效。
 *
 * <p>功能名约定：小写短横线（如 {@code search}），header 中大小写不敏感。
 */
public final class ExperimentFeatures {

    /**
     * 搜索检索优化：fuzzymatch 条件的 SQL 层 REGEXP 等价改写 + 嵌入式 Lucene 路由。
     * 关闭时 fuzzymatch 完全走原 geelato.gfn_fuzzymatch 存储函数路径。
     * 注意：索引同步/补偿/对账等数据准备路径不受实验开关控制（由模块开关
     * {@code geelato.search.enabled} 管理），保持预热，任意时刻开启即可用。
     */
    public static final String SEARCH = "search";

    private ExperimentFeatures() {
    }

    /**
     * 构建期允许经 header 开启的实验特性白名单。未登记的功能名在运行时
     * 永远不生效（header 传了也无效，仅记 debug 日志）。
     */
    public static Set<String> allowed() {
        return Set.of(SEARCH);
    }
}
