package cn.geelato.metasync.fix;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.web.platform.srv.meta.codegen.EntityJavaSourceGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 实体定义/物理表 → Java 源码（复用 {@link EntityJavaSourceGenerator}）。
 *
 * @author geemeta
 */
public class JavaSourceWriter {

    private static final Logger log = LoggerFactory.getLogger(JavaSourceWriter.class);

    private final EntityJavaSourceGenerator generator = new EntityJavaSourceGenerator();

    /**
     * 按 entityName 生成 Java 源码。
     * <p>实体定义以数据库（platform_dev_table/column）为准：生成前先强制从库刷新该实体的元数据，
     * 绕过启动时加载的旧缓存，保证生成源码与库中最新定义一致（实时读库，非缓存读取）。
     *
     * @param entityName  实体名
     * @param packageName 目标包名，为空用默认 cn.geelato.meta
     * @return 完整 .java 源码字符串
     */
    public String generate(String entityName, String packageName) {
        refreshFromDatabase(entityName);
        return generator.generate(entityName, packageName);
    }

    /**
     * 强制从数据库刷新指定实体的元数据缓存：
     * 先移除旧缓存（规避 refreshDBMeta 对 platform catalog 实体的拒绝），再触发按 entityName 重新装载。
     * <p>刷新失败不阻断，交由代码生成器按最新缓存状态处理（找不到时返回 not found 提示）。
     */
    private void refreshFromDatabase(String entityName) {
        if (entityName == null || entityName.trim().isEmpty()) {
            return;
        }
        try {
            MetaManager metaManager = MetaManager.singleInstance();
            metaManager.removeOne(entityName);
            metaManager.refreshDBMeta(entityName);
        } catch (Exception e) {
            log.warn("刷新实体定义（{}）失败，将按现有缓存生成：{}", entityName, e.getMessage());
        }
    }
}
