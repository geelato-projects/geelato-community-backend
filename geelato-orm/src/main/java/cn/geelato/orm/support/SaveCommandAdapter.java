package cn.geelato.orm.support;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.orm.query.MetaInsert;
import cn.geelato.orm.query.MetaUpdate;
import cn.geelato.orm.value.ValueRef;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.UIDGenerator;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Save Builder 到 SaveCommand 的适配器。
 */
public final class SaveCommandAdapter {

    private static final String FN_UPDATE_AT = "updateAt";
    private static final String FN_UPDATER = "updater";
    private static final String FN_UPDATER_NAME = "updaterName";
    private static final String FN_CREATE_AT = "createAt";
    private static final String FN_CREATOR = "creator";
    private static final String FN_CREATOR_NAME = "creatorName";
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat(DateUtils.DATETIME);
    private static final MetaManager META_MANAGER = MetaManager.singleInstance();

    private SaveCommandAdapter() {
    }

    public static SaveCommand fromInsert(MetaInsert insert) {
        SaveCommand command = new SaveCommand();
        String entityName = insert.resolveEntityName();
        EntityMeta entityMeta = META_MANAGER.getByEntityName(entityName);
        String pkField = entityMeta.getId().getFieldName();
        command.setEntityName(entityName);
        command.setCommandType(CommandType.Insert);

        Map<String, Object> entityMap = META_MANAGER.newDefaultEntityMap(entityName);
        Object pkValue = insert.getValueMap().get(pkField);
        if (pkValue == null) {
            pkValue = UIDGenerator.generate();
        }
        entityMap.put(pkField, stringifyValue(pkValue));
        insert.getValueMap().forEach((key, value) -> entityMap.put(key, stringifyValue(value)));
        putInsertDefaultField(entityMap);

        String[] fields = entityMap.keySet().toArray(new String[0]);
        command.setFields(fields);
        command.setValueMap(entityMap);
        command.setPK(String.valueOf(entityMap.get(pkField)));

        if (!insert.getChildren().isEmpty()) {
            insert.getChildren().forEach(child -> {
                SaveCommand childCommand = fromInsert(child);
                childCommand.setParentCommand(command);
                command.getCommands().add(childCommand);
            });
        }
        return command;
    }

    public static SaveCommand fromUpdate(MetaUpdate update) {
        SaveCommand command = new SaveCommand();
        String entityName = update.resolveEntityName();
        EntityMeta entityMeta = META_MANAGER.getByEntityName(entityName);
        String pkField = entityMeta.getId().getFieldName();
        command.setEntityName(entityName);
        command.setCommandType(CommandType.Update);

        Map<String, Object> params = new HashMap<>();
        update.getValueMap().forEach((key, value) -> params.put(key, stringifyValue(value)));
        FilterGroup where = FilterAdapter.adapt(update.getFilters());
        Object pkValue = params.get(pkField);
        if (where == null) {
            if (pkValue == null) {
                throw new IllegalArgumentException("更新操作缺少 where 条件或主键值: " + entityName);
            }
            where = new FilterGroup();
            where.addFilter(pkField, String.valueOf(pkValue));
        }
        if (pkValue == null && where.getFilters() != null) {
            pkValue = where.getFilters().stream()
                    .filter(filter -> pkField.equals(filter.getField()))
                    .map(FilterGroup.Filter::getValue)
                    .findFirst()
                    .orElse(null);
        }
        params.remove(pkField);
        putUpdateDefaultField(META_MANAGER.newDefaultEntityMap(entityName), params);

        command.setWhere(where);
        command.setFields(params.keySet().toArray(new String[0]));
        command.setValueMap(params);
        command.setPK(pkValue != null ? String.valueOf(pkValue) : "");

        if (!update.getChildren().isEmpty()) {
            update.getChildren().forEach(child -> {
                SaveCommand childCommand = fromInsert(child);
                childCommand.setParentCommand(command);
                command.getCommands().add(childCommand);
            });
        }
        return command;
    }

    private static Object stringifyValue(Object value) {
        if (value instanceof ValueRef ref) {
            return switch (ref.type()) {
                case CTX -> "$ctx." + ref.expression();
                case FN -> "$fn." + ref.expression();
                case PARENT -> "$parent." + ref.expression();
            };
        }
        return value;
    }

    private static void putInsertDefaultField(Map<String, Object> entity) {
        if (entity.containsKey(FN_CREATE_AT)) {
            entity.put(FN_CREATE_AT, SIMPLE_DATE_FORMAT.format(new java.util.Date()));
        }
        if (entity.containsKey(FN_CREATOR)) {
            entity.put(FN_CREATOR, SessionCtx.getUserId());
        }
        if (entity.containsKey(FN_CREATOR_NAME)) {
            entity.put(FN_CREATOR_NAME, SessionCtx.getUserName());
        }
    }

    private static void putUpdateDefaultField(Map<String, Object> entityDefaults, Map<String, Object> params) {
        if (entityDefaults.containsKey(FN_UPDATE_AT)) {
            params.put(FN_UPDATE_AT, SIMPLE_DATE_FORMAT.format(new java.util.Date()));
        }
        if (entityDefaults.containsKey(FN_UPDATER)) {
            params.put(FN_UPDATER, SessionCtx.getUserId());
        }
        if (entityDefaults.containsKey(FN_UPDATER_NAME)) {
            params.put(FN_UPDATER_NAME, SessionCtx.getUserName());
        }
    }
}
