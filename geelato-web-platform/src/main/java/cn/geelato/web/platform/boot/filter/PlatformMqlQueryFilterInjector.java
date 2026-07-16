package cn.geelato.web.platform.boot.filter;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.spi.MqlQueryFilterInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PlatformMqlQueryFilterInjector implements MqlQueryFilterInjector {
    private static final Logger log = LoggerFactory.getLogger(PlatformMqlQueryFilterInjector.class);

    private final PlatformQueryFilterSupport ruleSupport = new PlatformQueryFilterSupport();

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void inject(QueryCommand command) {
        log.info("Platform MQL query filter injector applying default filters. entityName={}", command.getEntityName());
        ruleSupport.applyDefaultFilters(command);
        log.info("Platform MQL query filter injector completed default filters. entityName={}", command.getEntityName());
    }
}
