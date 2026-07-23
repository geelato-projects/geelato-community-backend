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

    /**
     * 是否启用。默认 true，保持平台应用原有行为。
     * <p>当某些应用（如客户门户）需用自定义的 {@link FluentQueryFilterInjector} 完全取代平台默认行为时，
     * 可通过 {@link #setEnabled(boolean)} 将本注入器置为禁用。禁用后不会参与运行期的“唯一启用注入器”判定，
     * 与其它启用的注入器不再冲突。</p>
     */
    private volatile boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
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
