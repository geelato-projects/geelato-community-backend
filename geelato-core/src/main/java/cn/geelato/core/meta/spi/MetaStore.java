package cn.geelato.core.meta.spi;

import cn.geelato.core.orm.Dao;

import java.util.Map;

/**
 * 元数据定义来源抽象。
 */
public interface MetaStore {

    MetaDefinitionBundle load(Dao dao, Map<String, String> params);

    MetaDefinitionBundle loadByEntityName(Dao dao, String entityName);

    MetaDefinitionBundle loadByViewName(Dao dao, String viewName);
}
