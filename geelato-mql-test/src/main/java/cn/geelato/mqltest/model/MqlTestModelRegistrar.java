package cn.geelato.mqltest.model;

import cn.geelato.core.enums.ViewTypeEnum;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.view.TableView;
import lombok.extern.slf4j.Slf4j;

/**
 * MQL 测试实体元数据注册器。
 * <p>
 * 在宿主应用启动时，将测试实体（mql_test_org/user/order/order_item + mql_test_order_view 视图）
 * 注册到 MetaManager，使 explain/execute/scenarios 端点能识别这些实体。
 * <p>
 * 注意：这里只注册实体元数据（用于 MQL 解析/SQL生成），不建表。
 * 建表通过 mql-test-schema.sql 或 /api/mql/scenarios/initSchema 端点。
 */
@Slf4j
public class MqlTestModelRegistrar {

    private static volatile boolean registered = false;

    /**
     * 注册测试实体元数据（幂等，只注册一次）。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        MetaManager mm = MetaManager.singleInstance();
        try {
            // 4 个表实体
            mm.parseOne(MqlTestOrgModel.class);
            mm.parseOne(MqlTestUserModel.class);
            mm.parseOne(MqlTestOrderModel.class);
            mm.parseOne(MqlTestOrderItemModel.class);
            // 强制设置 dbType=mysql（保证反引号引用符）
            setDbType(mm, "mql_test_org");
            setDbType(mm, "mql_test_user");
            setDbType(mm, "mql_test_order");
            setDbType(mm, "mql_test_order_item");
            // 1 个视图实体
            registerOrderView(mm);
            registered = true;
            log.info("MQL 测试实体元数据已注册: mql_test_org/user/order/order_item + mql_test_order_view");
        } catch (Exception e) {
            log.warn("注册 MQL 测试实体元数据失败: {}", e.getMessage());
        }
    }

    private static void setDbType(MetaManager mm, String entityName) {
        EntityMeta em = mm.getByEntityName(entityName);
        if (em != null && em.getTableMeta() != null) {
            em.getTableMeta().setDbType("mysql");
        }
    }

    /**
     * 编程式注册视图实体 mql_test_order_view（DEFAULT 类型，带 @pf 模板）。
     * <p>
     * 通过 {@link MetaManager#parseViewEntity(TableView)} 注册，
     * 提供 view_column 列定义（JSON 字符串）与 view_construct 模板。
     */
    private static void registerOrderView(MetaManager mm) {
        String viewName = "mql_test_order_view";
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

        // view_construct：视图构造 SQL，含 @pf 模板参数。
        // 渲染规则：#...# 为一个 segment，segment 内含 {param} 且 param 为空时整段消除；
        // segment 内无 {param} 时原样保留。故 where 1=1 独立成一个 segment 保证始终保留。
        String viewConstruct = "select o.id, o.order_no, o.user_id, o.amount, o.status "
                + "from mql_test_order o "
                + "# where 1=1 # "
                + "# {statusFilter} and o.status = {statusFilter} # "
                + "# {minAmount} and o.amount >= {minAmount} #";

        TableView view = new TableView();
        view.setViewName(viewName);
        view.setTitle("订单视图(测试)");
        view.setViewType(ViewTypeEnum.VIRTUAL.getCode());
        view.setViewConstruct(viewConstruct);
        view.setViewColumn(viewColumn);

        mm.parseViewEntity(view);

        // 注册后强制设置 dbType=mysql（parseViewEntity 不会自动设）
        setDbType(mm, viewName);
    }
}
