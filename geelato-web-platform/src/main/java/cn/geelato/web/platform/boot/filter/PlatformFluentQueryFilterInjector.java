package cn.geelato.web.platform.boot.filter;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.spi.FluentQueryFilterInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PlatformFluentQueryFilterInjector implements FluentQueryFilterInjector {
    private static final Logger log = LoggerFactory.getLogger(PlatformFluentQueryFilterInjector.class);

    private final PlatformQueryFilterSupport ruleSupport = new PlatformQueryFilterSupport();

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void inject(QueryCommand command, MetaQuery query) {
        log.info("Platform Fluent query filter injector applying default filters. entityName={}",
                query == null ? null : query.resolveEntityName());
        ruleSupport.applyDefaultFilters(command);
        log.info("Platform Fluent query filter injector completed default filters. entityName={}",
                query == null ? null : query.resolveEntityName());
    }
}
