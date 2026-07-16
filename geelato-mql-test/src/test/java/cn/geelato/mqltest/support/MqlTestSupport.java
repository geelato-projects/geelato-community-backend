package cn.geelato.mqltest.support;

import cn.geelato.core.enums.ViewTypeEnum;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.mql.parser.JsonTextQueryParser;
import cn.geelato.core.sql.provider.MetaQuerySqlProvider;
import cn.geelato.mqltest.testmodel.MqlTestOrder;
import cn.geelato.mqltest.testmodel.MqlTestOrderItem;
import cn.geelato.mqltest.testmodel.MqlTestOrg;
import cn.geelato.mqltest.testmodel.MqlTestUser;
import org.junit.jupiter.api.BeforeAll;

/**
 * MQL 测试基类。
 * <p>
 * 负责在测试启动前将 5 个专用测试实体注册到 {@link MetaManager} 单例中，
 * 并提供解析与 SQL 生成的便捷方法。
 * <p>
 * 关键技术处理：
 * <ul>
 *   <li>显式给测试实体的 {@link TableMeta} 设置 dbType="mysql"，强制反引号引用符，
 *       绕开 {@code resolveEffectiveDbType} 对 {@code DataSourceManager} 的依赖
 *       （单元测试无数据源时会回退为双引号）。</li>
 *   <li>SQL 生成统一使用 {@link #newProvider()}（每次 new 一个新实例），
 *       保证 {@code tableAlias} 从 t0 开始，可预测。</li>
 * </ul>
 */
public abstract class MqlTestSupport {

    public static final String ENTITY_ORG = "mql_test_org";
    public static final String ENTITY_USER = "mql_test_user";
    public static final String ENTITY_ORDER = "mql_test_order";
    public static final String ENTITY_ORDER_ITEM = "mql_test_order_item";
    public static final String ENTITY_ORDER_VIEW = "mql_test_order_view";

    /**
     * 容器启动时（整个测试类）注册一次元数据。
     * MetaManager 是单例，parseOne 只在实体不存在时才加入。
     */
    @BeforeAll
    protected static void registerTestModels() {
        MetaManager mm = MetaManager.singleInstance();
        // 确保基础元数据已注册
        mm.parseOne(TableMeta.class);
        // 注册 4 个表实体
        mm.parseOne(MqlTestOrg.class);
        mm.parseOne(MqlTestUser.class);
        mm.parseOne(MqlTestOrder.class);
        mm.parseOne(MqlTestOrderItem.class);
        // 注册 1 个视图实体
        registerOrderView(mm);
    }

    /**
     * 编程式注册视图实体 mql_test_order_view（DEFAULT 类型，带 @pf 模板）。
     * <p>
     * 通过 {@link MetaManager#parseViewEntity(java.util.Map)} 注册，
     * 提供 view_column 列定义（JSON 字符串格式）与 view_construct 模板。
     */
    private static void registerOrderView(MetaManager mm) {
        String viewName = ENTITY_ORDER_VIEW;
        if (mm.containsEntity(viewName)) {
            return;
        }
        // view_column：列定义（JSON 字符串），格式同数据库 platform_dev_column 行
        String viewColumn = "["
                + "{\"field_name\":\"id\",\"column_name\":\"id\",\"title\":\"ID\",\"data_type\":\"BIGINT\",\"column_key\":true},"
                + "{\"field_name\":\"orderNo\",\"column_name\":\"order_no\",\"title\":\"订单号\",\"data_type\":\"VARCHAR\"},"
                + "{\"field_name\":\"userId\",\"column_name\":\"user_id\",\"title\":\"用户\",\"data_type\":\"BIGINT\"},"
                + "{\"field_name\":\"amount\",\"column_name\":\"amount\",\"title\":\"金额\",\"data_type\":\"DECIMAL\"},"
                + "{\"field_name\":\"status\",\"column_name\":\"status\",\"title\":\"状态\",\"data_type\":\"VARCHAR\"}"
                + "]";

        // view_construct：视图构造 SQL，含 @pf 模板参数 #{param}#
        // 参数：statusFilter（状态过滤）、minAmount（最小金额过滤），空值时整段消除
        String viewConstruct = "select o.id, o.order_no, o.user_id, o.amount, o.status "
                + "from mql_test_order o "
                + "#where 1=1"
                + "{statusFilter} and o.status = {statusFilter}#"
                + "{minAmount} and o.amount >= {minAmount}#"
                + "#";

        java.util.Map<String, Object> viewMap = new java.util.HashMap<>();
        viewMap.put("view_name", viewName);
        viewMap.put("title", "订单视图(测试)");
        viewMap.put("view_type", ViewTypeEnum.DEFAULT.getCode());
        viewMap.put("view_construct", viewConstruct);
        viewMap.put("view_column", viewColumn);

        mm.parseViewEntity(viewMap);

        // 注册后强制设置 dbType=mysql（parseViewEntity 不会自动设）
        EntityMeta em = mm.getByEntityName(viewName);
        if (em != null && em.getTableMeta() != null) {
            em.getTableMeta().setDbType("mysql");
        }
    }

    // ==================== 元数据 dbType 强制设置 ====================

    /**
     * 获取实体元数据，并确保其 dbType="mysql"（强制反引号引用符）。
     * 单元测试无数据源时，resolveEffectiveDbType 会回退双引号，导致断言失败。
     */
    protected EntityMeta getEntityMetaWithMySqlType(String entityName) {
        EntityMeta em = MetaManager.singleInstance().getByEntityName(entityName);
        if (em != null && em.getTableMeta() != null) {
            em.getTableMeta().setDbType("mysql");
        }
        return em;
    }

    // ==================== 解析与生成便捷方法 ====================

    /**
     * 解析 MQL JSON 文本为 QueryCommand。
     */
    protected QueryCommand parse(String mqlJson) {
        JsonTextQueryParser parser = new JsonTextQueryParser();
        return parser.parse(mqlJson);
    }

    /**
     * 创建新的 MetaQuerySqlProvider 实例（保证 tableAlias 从 t0 开始，可预测）。
     */
    protected MetaQuerySqlProvider newProvider() {
        return new MetaQuerySqlProvider();
    }

    /**
     * 生成查询 SQL（不分页）。
     * 使用前会强制设置实体的 dbType=mysql。
     */
    protected BoundSql generateSql(QueryCommand command) {
        getEntityMetaWithMySqlType(command.getEntityName());
        return newProvider().generate(command);
    }

    /**
     * 生成分页查询 SQL（含 count SQL）。
     */
    protected BoundPageSql generatePageSql(QueryCommand command) {
        getEntityMetaWithMySqlType(command.getEntityName());
        MetaQuerySqlProvider provider = newProvider();
        BoundPageSql bps = new BoundPageSql();
        bps.setBoundSql(provider.generate(command));
        bps.setCountSql(provider.buildCountSql(command));
        return bps;
    }
}
