package cn.geelato.orm.spi;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.orm.query.MetaQuery;

public interface FluentQueryFilterInjector {

    boolean isEnabled();

    void inject(QueryCommand command, MetaQuery query);

    /**
     * 是否强制注入；默认 false。
     * 实现类通常无需关心此方法，自动继承默认值；
     * 仅当需要让查询层的 disableInjectFilter 对本注入器失效时，覆盖返回 true。
     *
     * @return true 表示强制注入（忽略本次查询的 disableInjectFilter）
     */
    default boolean isForceInject() {
        return false;
    }
}
