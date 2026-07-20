package cn.geelato.mqltest.model;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.TableMeta;
import lombok.extern.slf4j.Slf4j;

/**
 * MQL 测试实体元数据注册器。
 * <p>
 * 在宿主应用启动时，将测试实体（mql_test_org/user/order/order_item）注册到 MetaManager，
 * 使 explain/execute/scenarios 端点能识别这些实体。
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
            mm.parseOne(MqlTestOrgModel.class);
            mm.parseOne(MqlTestUserModel.class);
            mm.parseOne(MqlTestOrderModel.class);
            mm.parseOne(MqlTestOrderItemModel.class);
            // 强制设置 dbType=mysql（保证反引号引用符）
            setDbType(mm, "mql_test_org");
            setDbType(mm, "mql_test_user");
            setDbType(mm, "mql_test_order");
            setDbType(mm, "mql_test_order_item");
            registered = true;
            log.info("MQL 测试实体元数据已注册: mql_test_org/user/order/order_item");
        } catch (Exception e) {
            log.warn("注册 MQL 测试实体元数据失败: {}", e.getMessage());
        }
    }

    private static void setDbType(MetaManager mm, String entityName) {
        var em = mm.getByEntityName(entityName);
        if (em != null && em.getTableMeta() != null) {
            em.getTableMeta().setDbType("mysql");
        }
    }
}
