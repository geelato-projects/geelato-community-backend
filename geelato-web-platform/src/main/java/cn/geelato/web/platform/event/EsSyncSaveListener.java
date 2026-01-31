package cn.geelato.web.platform.event;

import cn.geelato.core.orm.event.SaveEventContext;
import cn.geelato.core.orm.event.AfterSaveEventListener;

import java.util.Map;

public class EsSyncSaveListener implements AfterSaveEventListener {
    private final EsSyncService esSyncService;

    public EsSyncSaveListener(EsSyncService esSyncService) {
        this.esSyncService = esSyncService;
    }

    @Override
    public boolean supports(SaveEventContext context) {
        return esSyncService != null && context.getCommand() != null;
    }
    @Override
    public boolean enabled(SaveEventContext context) {
        return esSyncService != null;
    }

    @Override
    public void beforeSave(SaveEventContext context) {}

    @Override
    public void afterSave(SaveEventContext context) {
        String entityName = context.getCommand().getEntityName();
        String pk = context.getCommand().getPK();
        Map<String, Object> data = context.getDao().queryByEntityNameAndPK(entityName, pk);
        esSyncService.sync(entityName, data);
    }
}
