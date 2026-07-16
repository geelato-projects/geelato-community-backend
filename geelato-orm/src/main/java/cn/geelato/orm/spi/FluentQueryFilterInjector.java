package cn.geelato.orm.spi;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.orm.query.MetaQuery;

public interface FluentQueryFilterInjector {

    boolean isEnabled();

    void inject(QueryCommand command, MetaQuery query);
}
