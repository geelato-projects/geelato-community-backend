package cn.geelato.core.meta.model.parser;

import cn.geelato.core.SessionCtx;
import cn.geelato.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.gql.command.CommandType;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.command.SaveCommand;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.IdEntity;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.utils.UIDGenerator;

import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author geemeta
 */
@Slf4j
public class EntitySaveParser {
    private final MetaManager metaManager = MetaManager.singleInstance();

    public SaveCommand parse(IdEntity object, SessionCtx sessionCtx) {
        EntityMeta entityMeta = metaManager.get(object.getClass());
        SaveCommand command = new SaveCommand();
        command.setEntityName(entityMeta.getEntityName());


        Map<String,Object> entity = new HashMap<>(entityMeta.getFieldMetas().size());
        try {
            for (FieldMeta fm : entityMeta.getFieldMetas()) {
                entity.put(fm.getFieldName(), PropertyUtils.getProperty(object, fm.getFieldName()));
            }
            String PK = entityMeta.getId().getFieldName();
            if (Strings.isNotBlank(object.getId())) {
                command.setCommandType(CommandType.Update);

                FilterGroup fg = new FilterGroup();
                fg.addFilter(PK, String.valueOf(entity.get(PK)));
                command.setWhere(fg);
                command.setCommandType(CommandType.Update);
                putUpdateDefaultField(entity, sessionCtx);

                String[] updateFields = new String[entity.size()];
                entity.keySet().toArray(updateFields);
                command.setFields(updateFields);
                command.setValueMap(entity);
            } else {
                command.setCommandType(CommandType.Insert);
                entity.put(PK, UIDGenerator.generate());
                putInsertDefaultField(entity, sessionCtx);
                String[] insertFields = new String[entity.size()];
                entity.keySet().toArray(insertFields);
                command.setFields(insertFields);
                command.setValueMap(entity);
            }
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            log.error(e.getMessage(),e);
        }

        return command;
    }

    private void putUpdateDefaultField(Map<String,Object> entity, SessionCtx sessionCtx) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DateUtils.DATETIME);
        if (entity.containsKey("updateAt")) {
            entity.put("updateAt",  simpleDateFormat.format(new Date()));
        }
        if (entity.containsKey("updater")) {
            entity.put("updater", SessionCtx.getUserId());
        }
        if (entity.containsKey("updaterName")) {
            entity.put("updaterName",  SessionCtx.getUserName());
        }
    }

    private void putInsertDefaultField(Map<String,Object> entity, SessionCtx sessionCtx) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DateUtils.DATETIME);
        if (entity.containsKey("createAt")) {
            entity.put("createAt", simpleDateFormat.format(new Date()));
        }
        if (entity.containsKey("creator")) {
            entity.put("creator", SessionCtx.getUserId());
        }
        if (entity.containsKey("creatorName")) {
            entity.put("creatorName", SessionCtx.getUserName());
        }
        if (entity.containsKey("buId")) {
            entity.put("buId", SessionCtx.getCurrentUser().getBuId());
        }
        if (entity.containsKey("deptId")) {
            entity.put("deptId", SessionCtx.getCurrentUser().getDefaultOrgId());
        }
        if (entity.containsKey("deleteAt")) {
            entity.put("deleteAt", DateUtils.DEFAULT_DELETE_AT);
        }
        putUpdateDefaultField(entity, sessionCtx);
    }


}
