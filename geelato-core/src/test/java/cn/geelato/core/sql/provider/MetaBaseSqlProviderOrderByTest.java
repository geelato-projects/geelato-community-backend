package cn.geelato.core.sql.provider;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Id;
import cn.geelato.lang.meta.Title;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * resolveOrderBy 的标识符引用行为：
 * <ul>
 *     <li>主实体字段/列名：前置主表别名并整体引用，如 {@code nu.`create_at` DESC}（既有行为）</li>
 *     <li>带表别名的多段标识符（JOIN 查询排序主体表字段，如 {@code n.priority}）：
 *         按段分别引用为 {@code n.`priority`}，不得整体包裹成 {@code `n.priority`}（历史上生成无效 SQL）</li>
 * </ul>
 */
class MetaBaseSqlProviderOrderByTest {

    private final MetaManager metaManager = MetaManager.singleInstance();
    private final MetaBaseSqlProvider<QueryCommand> provider = new MetaBaseSqlProvider<>() {
        @Override
        protected String buildOneSql(QueryCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Object[] buildParams(QueryCommand command) {
            return new Object[0];
        }

        @Override
        protected int[] buildTypes(QueryCommand command) {
            return new int[0];
        }
    };

    private EntityMeta em;

    @BeforeEach
    void setUp() {
        metaManager.parseOne(OrderByTestEntity.class);
        em = metaManager.get(OrderByTestEntity.class);
        em.getTableMeta().setDbType("mysql");
    }

    @AfterEach
    void cleanUp() {
        metaManager.removeOne("order_by_test_entity");
    }

    @Test
    void fieldNameIsResolvedToColumnWithMainAlias() {
        assertEquals("nu.`create_at` DESC", provider.resolveOrderBy(em, "createAt DESC", "nu"));
    }

    @Test
    void qualifiedIdentifierIsQuotedPerSegmentNotAsWhole() {
        // JOIN 查询排序主体表字段：分段引用（别名段一并引用），不再整体包裹成 `n.priority`
        assertEquals("`n`.`priority` DESC", provider.resolveOrderBy(em, "n.priority DESC", "nu"));
    }

    @Test
    void multipleQualifiedOrdersAreJoinedWithComma() {
        assertEquals("`n`.`priority` DESC,`n`.`create_at` DESC",
                provider.resolveOrderBy(em, "n.priority DESC,n.create_at DESC", "nu"));
    }

    @Test
    void unknownFieldIsKeptLiteralWithMainAlias() {
        // 主实体不存在的字段按字面输出（既有回退行为不变）
        assertEquals("nu.`unknown_col` ASC", provider.resolveOrderBy(em, "unknown_col ASC", "nu"));
    }

    // 注意：MetaReflex.getBean 用反射 newInstance 读字段默认值，构造器必须可反射实例化（public）
    @Title(title = "OrderByTestEntity")
    @Entity(name = "order_by_test_entity", catalog = "platform")
    public static class OrderByTestEntity {
        @Id
        private String id;
        @Col(name = "create_at")
        private Date createAt;
    }
}
