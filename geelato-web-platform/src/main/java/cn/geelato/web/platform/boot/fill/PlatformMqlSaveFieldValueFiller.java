package cn.geelato.web.platform.boot.fill;

import cn.geelato.core.mql.spi.MqlSaveFieldValueFillContext;
import cn.geelato.core.mql.spi.MqlSaveFieldValueFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PlatformMqlSaveFieldValueFiller implements MqlSaveFieldValueFiller {
    private static final Logger log = LoggerFactory.getLogger(PlatformMqlSaveFieldValueFiller.class);

    private final PlatformFieldValueFillSupport support = new PlatformFieldValueFillSupport();

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void fill(MqlSaveFieldValueFillContext context) {
        log.info("Platform MQL save field filler applying defaults. entityName={}, commandType={}",
                context.getEntityName(), context.getCommandType());
        support.applyMqlDefaults(context);
        log.info("Platform MQL save field filler completed defaults. entityName={}, commandType={}",
                context.getEntityName(), context.getCommandType());
    }
}
