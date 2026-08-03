package cn.geelato.metasync.fix;

import cn.geelato.web.platform.srv.meta.codegen.EntityJavaSourceGenerator;

/**
 * 实体定义/物理表 → Java 源码（复用 {@link EntityJavaSourceGenerator}）。
 *
 * @author geemeta
 */
public class JavaSourceWriter {

    private final EntityJavaSourceGenerator generator = new EntityJavaSourceGenerator();

    /**
     * 按 entityName 生成 Java 源码。
     *
     * @param entityName 实体名（必须在 MetaManager 中已加载）
     * @param packageName 目标包名，为空用默认 cn.geelato.meta
     * @return 完整 .java 源码字符串
     */
    public String generate(String entityName, String packageName) {
        return generator.generate(entityName, packageName);
    }
}
