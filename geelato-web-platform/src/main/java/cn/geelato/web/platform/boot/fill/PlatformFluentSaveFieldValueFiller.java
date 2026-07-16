package cn.geelato.web.platform.boot.fill;

import cn.geelato.orm.spi.FluentSaveFieldValueFillContext;
import cn.geelato.orm.spi.FluentSaveFieldValueFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PlatformFluentSaveFieldValueFiller implements FluentSaveFieldValueFiller {
    private static final Logger log = LoggerFactory.getLogger(PlatformFluentSaveFieldValueFiller.class);

    private final PlatformFieldValueFillSupport support = new PlatformFieldValueFillSupport();

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void fill(FluentSaveFieldValueFillContext context) {
        log.info("Platform Fluent save field filler applying defaults. entityName={}, commandType={}",
                context.getEntityName(), context.getCommandType());
        support.applyFluentDefaults(context);
        log.info("Platform Fluent save field filler completed defaults. entityName={}, commandType={}",
                context.getEntityName(), context.getCommandType());
    }
}
