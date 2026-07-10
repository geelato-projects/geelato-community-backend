package cn.geelato.orm.support;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.orm.query.MetaDelete;
import cn.geelato.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * MetaDelete 到 DeleteCommand 的适配器。
 */
public final class DeleteCommandAdapter {

    private static final String FN_DEL_STATUS = "delStatus";
    private static final String FN_DELETE_AT = "deleteAt";
    private static final String FN_UPDATE_AT = "updateAt";
    private static final String FN_UPDATER = "updater";
    private static final String FN_UPDATER_NAME = "updaterName";
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat(DateUtils.DATETIME);
    private static final MetaManager META_MANAGER = MetaManager.singleInstance();

    private DeleteCommandAdapter() {
    }

    public static DeleteCommand from(MetaDelete delete) {
        DeleteCommand command = new DeleteCommand();
        String entityName = delete.resolveEntityName();
        command.setEntityName(entityName);
        command.setConnectId(delete.getConnectId());
        command.setCommandType(CommandType.Delete);
        command.setWhere(FilterAdapter.adapt(delete.getFilters()));
        command.setValueMap(buildDeleteValueMap(entityName));
        command.setFields(command.getValueMap().keySet().toArray(new String[0]));
        return command;
    }

    private static Map<String, Object> buildDeleteValueMap(String entityName) {
        Map<String, Object> entityDefaults = META_MANAGER.newDefaultEntityMap(entityName);
        Map<String, Object> params = new HashMap<>();
        String now = SIMPLE_DATE_FORMAT.format(new java.util.Date());
        if (entityDefaults.containsKey(FN_DEL_STATUS)) {
            params.put(FN_DEL_STATUS, 1);
        }
        if (entityDefaults.containsKey(FN_DELETE_AT)) {
            params.put(FN_DELETE_AT, now);
        }
        if (entityDefaults.containsKey(FN_UPDATE_AT)) {
            params.put(FN_UPDATE_AT, now);
        }
        if (entityDefaults.containsKey(FN_UPDATER)) {
            params.put(FN_UPDATER, SessionCtx.getUserId());
        }
        if (entityDefaults.containsKey(FN_UPDATER_NAME)) {
            params.put(FN_UPDATER_NAME, SessionCtx.getUserName());
        }
        return params;
    }
}
