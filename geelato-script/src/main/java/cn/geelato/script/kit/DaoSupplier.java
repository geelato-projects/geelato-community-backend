package cn.geelato.script.kit;

import cn.geelato.core.orm.Dao;

/**
 * 平台 ORM Dao 供给（Spring 宿主取 dynamicDao bean；独立宿主以 DataSource 自建）。
 */
@FunctionalInterface
public interface DaoSupplier {

    Dao get();
}
