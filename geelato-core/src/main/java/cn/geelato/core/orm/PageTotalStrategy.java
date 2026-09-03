package cn.geelato.core.orm;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.filter.FilterGroup;

/**
 * 分页查询 total（count SQL）可否从已返回数据行精确推导的判定。
 * <p>
 * count SQL 是数据 SQL 外包一层 {@code select count(*) from (...) t}（见
 * {@code MetaQuerySqlProvider.buildCountSql}），两者共享同一份 select/from/join/where/group by/having，
 * 因此以下三类场景中 count 的结果可从数据行数学推导，跳过 count SQL 后 total 与真实总数恒等，
 * 对前端零感知：
 * <ol>
 *   <li><b>R0 全量查询</b>：未开启分页（{@link QueryCommand#isPagingQuery()} 为 false，如 /meta/list 未传 @p）
 *       时数据 SQL 不带 limit，返回的即是全量，count 恒等于行数；</li>
 *   <li><b>R1 主键等值检索</b>：where 中处于 and 位置的主键 eq 条件将结果约束为 ≤1 行
 *       （如 /meta/list 按 id 检索、@p=1,1）；</li>
 *   <li><b>R2 首页未满</b>：pageNum=1 且返回行数 &lt; pageSize 时，后续必然没有更多行。</li>
 * </ol>
 * 判定原则：<b>宁可漏跳（多执行一次 count，行为与现状一致），不可错跳（total 失真）</b>。
 * 因此 R1 附带保守排除项：
 * <ul>
 *   <li>foreignFields / joins / ref 字段条件存在时不判定——join 可能让 1 行主记录放大成多行
 *       （count 统计的是放大后的行数，limit 截断后 rows.size() 可能小于 count）；</li>
 *   <li>只沿 logic=and 的组递归——or 位置的主键 eq 不构成 ≤1 上界；这与
 *       {@code MetaBaseSqlProvider.buildConditions} 的组间拼接语义一致（子组以父组 logic 挂接）。</li>
 * </ul>
 * 其余不可推导的场景（如非首页、首页恰好取满且无主键条件）仍须执行 count，total 不允许返回不精确值。
 */
public final class PageTotalStrategy {

    private PageTotalStrategy() {
    }

    /**
     * 数据行已知后，total 是否可精确推导（R0/R1/R2 任一命中）。
     *
     * @param uniqueKeyBounded 查询前经 {@link #uniqueKeyBounded(QueryCommand, MetaManager)} 判定的 R1 结果
     * @param command          查询命令（含最终 pageNum/pageSize，SPI 注入与改写后的 where）
     * @param rowNum           数据 SQL 实际返回行数
     */
    public static boolean totalDerivable(boolean uniqueKeyBounded, QueryCommand command, int rowNum) {
        return uniqueKeyBounded || totalDerivableFromRows(command, rowNum);
    }

    /**
     * 仅依据分页参数与返回行数的可推导判定（R0/R2，不含 R1）。
     */
    public static boolean totalDerivableFromRows(QueryCommand command, int rowNum) {
        if (command == null) {
            return false;
        }
        // R0：非分页查询无 limit，数据行即全量
        if (!command.isPagingQuery()) {
            return true;
        }
        // R2：第 1 页未取满则后面必然没有更多行；pageNum>1 时前页是否取满未知，不可推导
        return command.getPageNum() == 1 && rowNum < command.getPageSize();
    }

    /**
     * R1 判定：按元数据解析实体后评估主键等值约束。
     * 实体不存在、无主键（视图等）或解析异常时按不可推导处理。
     */
    public static boolean uniqueKeyBounded(QueryCommand command, MetaManager metaManager) {
        if (command == null || metaManager == null || command.getEntityName() == null) {
            return false;
        }
        EntityMeta em;
        try {
            em = metaManager.getByEntityName(command.getEntityName());
        } catch (Exception ex) {
            return false;
        }
        return uniqueKeyBounded(command, em);
    }

    /**
     * R1 判定（结构扫描，可脱离 MetaManager 单测）。
     */
    public static boolean uniqueKeyBounded(QueryCommand command, EntityMeta em) {
        if (command == null || em == null || em.getId() == null || command.getWhere() == null) {
            return false;
        }
        if (command.getForeignFields() != null && command.getForeignFields().length > 0) {
            return false;
        }
        if (command.getJoins() != null && !command.getJoins().isEmpty()) {
            return false;
        }
        return scan(em, command.getWhere(), true) == ScanResult.BOUNDED;
    }

    /**
     * 扫描结果：BOUNDED=找到 and 位置的主键等值；DISQUALIFIED=存在保守排除项（ref 字段）；
     * CONTINUE=本组及子组均未发现可判定条件。
     */
    private enum ScanResult {
        BOUNDED, CONTINUE, DISQUALIFIED
    }

    /**
     * 递归扫描过滤组，寻找处于 and 位置的主键 eq 条件。
     *
     * @param inAndPosition 当前组整体是否处于 and 位置（根组视为是；子组以父组 logic 挂接，
     *                      仅当父组处于 and 位置且父组 logic=and 时子组才处于 and 位置）
     */
    private static ScanResult scan(EntityMeta em, FilterGroup group, boolean inAndPosition) {
        if (group == null) {
            return ScanResult.CONTINUE;
        }
        FilterGroup.Logic logic = group.getLogic() == null ? FilterGroup.Logic.and : group.getLogic();
        boolean effective = inAndPosition && logic == FilterGroup.Logic.and;
        boolean bounded = false;
        for (FilterGroup.Filter filter : group.getFilters()) {
            if (filter == null) {
                continue;
            }
            // ref 字段条件可能引入跨表解析，保守排除整个查询
            if (filter.isRefField()) {
                return ScanResult.DISQUALIFIED;
            }
            if (effective && !bounded
                    && filter.getOperator() == FilterGroup.Operator.eq
                    && isPrimaryKeyField(em, filter.getField())) {
                bounded = true;
            }
        }
        for (FilterGroup child : group.getChildFilterGroup()) {
            ScanResult result = scan(em, child, effective);
            if (result == ScanResult.DISQUALIFIED) {
                return ScanResult.DISQUALIFIED;
            }
            if (result == ScanResult.BOUNDED) {
                bounded = true;
            }
        }
        return bounded ? ScanResult.BOUNDED : ScanResult.CONTINUE;
    }

    /**
     * 字段是否解析为主键列。与 {@code MetaBaseSqlProvider.buildConditions} 一致按字段名经
     * {@code em.getFieldMeta(field)} 解析；解析不到（含列名误传，该场景条件构建本就会硬失败）不算主键。
     */
    private static boolean isPrimaryKeyField(EntityMeta em, String field) {
        if (field == null || field.isEmpty()) {
            return false;
        }
        try {
            FieldMeta fm = em.getFieldMeta(field);
            return fm != null && fm.getColumnName() != null
                    && fm.getColumnName().equals(em.getId().getColumnName());
        } catch (Exception ex) {
            return false;
        }
    }
}
