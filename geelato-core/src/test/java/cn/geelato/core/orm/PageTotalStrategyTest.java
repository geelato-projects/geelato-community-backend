package cn.geelato.core.orm;

import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.QueryJoin;
import cn.geelato.core.mql.filter.FilterGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PageTotalStrategy} 判定规则单测。
 * <p>
 * 覆盖 R0（全量）/R2（首页未满）的全部边界，以及 R1（主键等值）的 and 位置语义与
 * 保守排除项（or 组、join、foreignFields、ref 字段、无主键实体）。
 */
@DisplayName("PageTotalStrategy：count 可推导判定")
class PageTotalStrategyTest {

    private static final String ENTITY = "mql_test_order";

    // ==================== 元数据与命令构造 ====================

    private static EntityMeta orderMeta() {
        EntityMeta em = new EntityMeta();
        FieldMeta id = field("id", "id");
        FieldMeta status = field("status", "status");
        FieldMeta orderNo = field("order_no", "orderNo");
        em.setFieldMetas(List.of(id, status, orderNo));
        em.setId(id);
        return em;
    }

    private static EntityMeta noIdMeta() {
        EntityMeta em = new EntityMeta();
        em.setFieldMetas(List.of(field("status", "status")));
        return em;
    }

    /** setFieldMetas 内部会取 fieldType.getSimpleName()，必须设置类型。 */
    private static FieldMeta field(String columnName, String fieldName) {
        FieldMeta fm = new FieldMeta(columnName, fieldName, fieldName);
        fm.setFieldType(String.class);
        return fm;
    }

    private static QueryCommand command(FilterGroup where) {
        QueryCommand command = new QueryCommand();
        command.setEntityName(ENTITY);
        command.setWhere(where);
        return command;
    }

    private static QueryCommand paged(FilterGroup where, int pageNum, int pageSize) {
        QueryCommand command = command(where);
        command.setPageNum(pageNum);
        command.setPageSize(pageSize);
        return command;
    }

    // ==================== R0 / R2：totalDerivableFromRows ====================

    @Test
    @DisplayName("R0：未开启分页（缺省 -1/-1）即可推导")
    void r0NonPagingDerivable() {
        QueryCommand command = command(new FilterGroup().addFilter("status", "done"));
        assertTrue(PageTotalStrategy.totalDerivableFromRows(command, 0));
        assertTrue(PageTotalStrategy.totalDerivableFromRows(command, 100));
    }

    @Test
    @DisplayName("R2：第 1 页未取满可推导")
    void r2FirstPageUnderfullDerivable() {
        assertTrue(PageTotalStrategy.totalDerivableFromRows(paged(new FilterGroup(), 1, 10), 3));
        assertTrue(PageTotalStrategy.totalDerivableFromRows(paged(new FilterGroup(), 1, 10), 0));
        // @p=1,1 且 0 行命中：无更多行
        assertTrue(PageTotalStrategy.totalDerivableFromRows(paged(new FilterGroup(), 1, 1), 0));
    }

    @Test
    @DisplayName("R2 反例：首页恰好取满 / 非首页均不可推导")
    void r2FullPageOrLaterPageNotDerivable() {
        // @p=1,1 恰好返回满 1 行：真实总数可能 >1
        assertFalse(PageTotalStrategy.totalDerivableFromRows(paged(new FilterGroup(), 1, 1), 1));
        assertFalse(PageTotalStrategy.totalDerivableFromRows(paged(new FilterGroup(), 1, 10), 10));
        // 第 2 页未满：前页是否取满未知，总数不可推导
        assertFalse(PageTotalStrategy.totalDerivableFromRows(paged(new FilterGroup(), 2, 10), 3));
        assertFalse(PageTotalStrategy.totalDerivableFromRows(paged(new FilterGroup(), 2, 10), 0));
    }

    @Test
    @DisplayName("R1 命中后与分页取满叠加仍可推导（id 检索 @p=1,1 场景）")
    void r1OverridesFullPage() {
        QueryCommand command = paged(new FilterGroup().addFilter("id", "1"), 1, 1);
        assertTrue(PageTotalStrategy.totalDerivable(true, command, 1));
        // 无 R1 时同参数不可推导
        assertFalse(PageTotalStrategy.totalDerivable(false, command, 1));
    }

    // ==================== R1：uniqueKeyBounded ====================

    @Test
    @DisplayName("R1：and 组内主键 eq（单条件/多条件）可判定")
    void r1PrimaryKeyEqBounds() {
        EntityMeta em = orderMeta();
        assertTrue(PageTotalStrategy.uniqueKeyBounded(command(new FilterGroup().addFilter("id", "1")), em));
        assertTrue(PageTotalStrategy.uniqueKeyBounded(command(
                new FilterGroup().addFilter("status", "done").addFilter("id", "1")), em));
    }

    @Test
    @DisplayName("R1：and 位置的子组内主键 eq 同样可判定")
    void r1PrimaryKeyEqInAndChildGroupBounds() {
        FilterGroup child = new FilterGroup().addFilter("id", "1");
        FilterGroup root = new FilterGroup().addFilter("status", "done");
        root.getChildFilterGroup().add(child);
        assertTrue(PageTotalStrategy.uniqueKeyBounded(command(root), orderMeta()));
    }

    @Test
    @DisplayName("R1 反例：or 位置的主键 eq 不构成上界")
    void r1OrGroupNotBounds() {
        // 根组为 or
        assertFalse(PageTotalStrategy.uniqueKeyBounded(
                command(new FilterGroup(FilterGroup.Logic.or).addFilter("id", "1").addFilter("status", "done")),
                orderMeta()));
        // 子组为 or（del_status 类写法）
        FilterGroup childOr = new FilterGroup(FilterGroup.Logic.or).addFilter("id", "1").addFilter("status", "done");
        FilterGroup root = new FilterGroup().addFilter("orderNo", "SO001");
        root.getChildFilterGroup().add(childOr);
        assertFalse(PageTotalStrategy.uniqueKeyBounded(command(root), orderMeta()));
    }

    @Test
    @DisplayName("R1 反例：非主键 eq / 未知字段 / 主键非 eq 操作符")
    void r1NonPrimaryKeyOrNonEqNotBounds() {
        EntityMeta em = orderMeta();
        assertFalse(PageTotalStrategy.uniqueKeyBounded(command(new FilterGroup().addFilter("status", "done")), em));
        assertFalse(PageTotalStrategy.uniqueKeyBounded(command(new FilterGroup().addFilter("nope", "1")), em));
        assertFalse(PageTotalStrategy.uniqueKeyBounded(
                command(new FilterGroup().addFilter("id", FilterGroup.Operator.in, "1,2")), em));
    }

    @Test
    @DisplayName("R1 反例：存在 join / foreignFields / ref 字段条件时不判定（join 可能放大行数）")
    void r1JoinLikeFactorsDisqualify() {
        EntityMeta em = orderMeta();
        QueryCommand withForeign = command(new FilterGroup().addFilter("id", "1"));
        withForeign.setForeignFields(new String[]{"userId->name"});
        assertFalse(PageTotalStrategy.uniqueKeyBounded(withForeign, em));

        QueryCommand withJoin = command(new FilterGroup().addFilter("id", "1"));
        withJoin.setJoins(Collections.singletonList(new QueryJoin()));
        assertFalse(PageTotalStrategy.uniqueKeyBounded(withJoin, em));

        // ref 字段（entity.field 形式）在任何位置都保守排除
        FilterGroup withRef = new FilterGroup().addFilter("id", "1")
                .addFilter(new FilterGroup.Filter("mql_test_user.name", FilterGroup.Operator.eq, "u1"));
        assertFalse(PageTotalStrategy.uniqueKeyBounded(command(withRef), em));
    }

    @Test
    @DisplayName("R1 反例：无主键实体（视图等）/ 无 where / null")
    void r1NoIdOrNoWhereNotBounds() {
        assertFalse(PageTotalStrategy.uniqueKeyBounded(command(new FilterGroup().addFilter("status", "done")), noIdMeta()));
        QueryCommand noWhere = new QueryCommand();
        noWhere.setEntityName(ENTITY);
        assertFalse(PageTotalStrategy.uniqueKeyBounded(noWhere, orderMeta()));
        assertFalse(PageTotalStrategy.uniqueKeyBounded(null, orderMeta()));
    }
}
