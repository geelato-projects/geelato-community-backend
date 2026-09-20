package cn.geelato.core.mql.spi;

import cn.geelato.core.mql.command.QueryCommand;

/**
 * MQL fuzzymatch 条件的检索引擎路由器 SPI。
 *
 * <p>多编号模糊检索场景下，{@code fuzzymatch($entity.col,'kw')|gt:0} 条件组由外部检索引擎
 * （如嵌入式 Lucene，经搜索抽象层接入）预先检索出主实体 id 集合，替换为 {@code id in (...)}，
 * 消除数据库侧逐行正则/存储函数扫描。join、分页、排序、数据权限仍由 SQL 完成。
 *
 * <p>实现约束（等价性硬约束，违反将导致查询结果集与原函数不一致）：
 * <ul>
 *   <li>仅当 or 关系的条件组内全部条件为同实体的 fuzzymatch|gt:0 时才可整组替换；
 *       fuzzymatch 条件与普通条件混合的 or 组不得替换（保留 SQL 侧 REGEXP 等价改写）；</li>
 *   <li>关键字含正则元字符（原函数按正则求值，见
 *       {@code FuzzymatchSupport#containsRegexMeta}）时不得路由；</li>
 *   <li>检索结果需应用与原查询一致的多租户/软删过滤；命中超过上限时不得截断（放弃路由并如实返回未替换）。</li>
 * </ul>
 *
 * <p>平台在 SQL 生成前调用（见 {@code MqlFuzzymatchRouteResolver}）；
 * 未装配实现或路由不适用时，条件走 SQL 层 REGEXP 等价改写兜底，查询链路不受影响。
 */
public interface MqlFuzzymatchRouter {

    /**
     * 尝试将 command 中可整组路由的 fuzzymatch 条件替换为 id-in 条件。
     *
     * @param command 已解析的查询命令
     * @return true 表示发生替换（SQL 将按 id-in 生成）；false 表示不适用，保持原条件
     */
    boolean route(QueryCommand command);
}
