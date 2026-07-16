package cn.geelato.core.mql.spi;

import cn.geelato.core.mql.command.QueryCommand;

public interface MqlQueryFilterInjector {

    boolean isEnabled();

    void inject(QueryCommand command);
}
