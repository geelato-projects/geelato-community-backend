package cn.geelato.core.meta.spi;

import java.util.Map;

/**
 * 元数据定义来源抽象。
 *
 * <p>本接口是框架层（geelato-core）的 SPI，定义“从何处装载元数据定义”。
 * 实现者自行决定如何获取数据（数据库、文件、内存……），框架层不强制依赖
 * 任何数据访问对象。需要数据库的实现（如 {@code DefaultMetaStore}）自行
 * 通过依赖注入持有 {@code Dao}。</p>
 */
public interface MetaStore {

    MetaDefinitionBundle load(Map<String, String> params);

    MetaDefinitionBundle loadByEntityName(String entityName);

    MetaDefinitionBundle loadByViewName(String viewName);
}
