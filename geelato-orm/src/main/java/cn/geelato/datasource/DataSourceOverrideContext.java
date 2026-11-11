package cn.geelato.datasource;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 请求级数据源覆盖上下文：覆盖生效期内，无显式数据源归属的实体路由到指定 key，可声明只读。
 * <p>中性路由能力，不感知调用方语义；开启与清理由上层（如平台层请求过滤器）负责，
 * 非请求线程（调度/后台任务）无此上下文，行为自然回退既有解析链。
 * <p>在解析优先级中的位置：实体显式归属 &gt; 作用域注解 &gt; 外层已显式设置的 key &gt;
 * <b>本覆盖</b> &gt; 平台默认 key &gt; primary——任何显式指定都不被请求级覆盖劫持。
 */
public final class DataSourceOverrideContext {

    /**
     * EntityMeta.catalog 的默认占位值，代表实体未声明任何 catalog 归属
     */
    private static final String CATALOG_NONE = "none";

    /**
     * 数据写方法名（cn.geelato.core.orm.Dao 继承链上的变更类方法，精确匹配）
     */
    private static final Set<String> WRITE_METHODS = Set.of(
            "save", "insert", "update", "delete",
            "multiSave", "multiDelete", "batchSave",
            "executeUpdate", "nativeExecute",
            "execute", "executeKey", "executeForNull");

    private static final ThreadLocal<DataSourceOverride> CURRENT = new ThreadLocal<>();

    private DataSourceOverrideContext() {
    }

    /**
     * 方法名是否属于数据写方法（cn.geelato.core.orm.Dao 继承链上的变更类方法，精确匹配）。
     * <p>供实体级只读门禁（如物化视图实体禁写）复用同一份名单。
     */
    public static boolean isWriteMethod(String methodName) {
        return methodName != null && WRITE_METHODS.contains(methodName);
    }

    public static void set(String connectId, boolean readOnly) {
        CURRENT.set(new DataSourceOverride(connectId, readOnly));
    }

    public static DataSourceOverride current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 实体是否无显式数据源归属：无 connectId 且未声明有效 catalog。
     * <p>catalog 有值（即使映射缺失）即视为显式归属，不参与覆盖；
     * 实体名缺失或元数据未登记同样返回 false，保持与未覆盖时一致的解析行为。
     */
    public static boolean isUnclaimed(String entityName) {
        if (!StringUtils.hasText(entityName)) {
            return false;
        }
        EntityMeta entityMeta = MetaManager.singleInstance().getByEntityName(entityName);
        if (entityMeta == null) {
            return false;
        }
        if (entityMeta.getTableMeta() != null
                && StringUtils.hasText(entityMeta.getTableMeta().getConnectId())) {
            return false;
        }
        String catalog = entityMeta.getCatalog();
        return !StringUtils.hasText(catalog) || CATALOG_NONE.equalsIgnoreCase(catalog);
    }

    /**
     * 覆盖期解析实体的数据源 key：覆盖未开启或实体有显式归属时返回 null（继续走既有解析链）。
     */
    public static String resolveOverrideKey(String entityName) {
        DataSourceOverride override = CURRENT.get();
        if (override == null || !isUnclaimed(entityName)) {
            return null;
        }
        return override.connectId();
    }

    /**
     * 只读覆盖下的写操作门禁：写方法作用于无归属（含实体名不可识别）实体时抛出明确异常；
     * 显式归属实体的写操作不受限。
     */
    public static void assertWritable(String methodName, String entityName) {
        DataSourceOverride override = CURRENT.get();
        if (override == null || !override.readOnly() || !WRITE_METHODS.contains(methodName)) {
            return;
        }
        if (!StringUtils.hasText(entityName) || isUnclaimed(entityName)) {
            throw new IllegalStateException(String.format(
                    "当前请求已切换至只读数据源 %s，禁止写操作：%s.%s",
                    override.connectId(), entityName, methodName));
        }
    }

    public record DataSourceOverride(String connectId, boolean readOnly) {
    }
}
