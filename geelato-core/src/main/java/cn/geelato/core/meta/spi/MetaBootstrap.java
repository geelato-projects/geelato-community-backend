package cn.geelato.core.meta.spi;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.orm.Dao;

/**
 * 元数据初始化钩子。
 */
public interface MetaBootstrap {

    void bootstrap(MetaManager metaManager, Dao dao);
}
