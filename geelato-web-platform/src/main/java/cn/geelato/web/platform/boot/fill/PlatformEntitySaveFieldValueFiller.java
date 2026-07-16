package cn.geelato.web.platform.boot.fill;

import cn.geelato.core.meta.spi.EntitySaveFieldValueFillContext;
import cn.geelato.core.meta.spi.EntitySaveFieldValueFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PlatformEntitySaveFieldValueFiller implements EntitySaveFieldValueFiller {
    private static final Logger log = LoggerFactory.getLogger(PlatformEntitySaveFieldValueFiller.class);

    private final PlatformFieldValueFillSupport support = new PlatformFieldValueFillSupport();

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void fill(EntitySaveFieldValueFillContext context) {
        log.info("Platform entity save field filler applying defaults. entityName={}, commandType={}",
                context.getEntityName(), context.getCommandType());
        support.applyEntityDefaults(context);
        log.info("Platform entity save field filler completed defaults. entityName={}, commandType={}",
                context.getEntityName(), context.getCommandType());
    }
}
