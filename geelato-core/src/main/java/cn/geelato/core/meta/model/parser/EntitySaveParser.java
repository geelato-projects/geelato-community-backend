package cn.geelato.core.meta.model.parser;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.meta.spi.EntitySaveFieldValueFillContext;
import cn.geelato.core.meta.spi.support.EntitySaveFieldValueFillRuntimeResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.IdEntity;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.utils.UIDGenerator;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author geemeta
 */
@Slf4j
public class EntitySaveParser {
    private final MetaManager metaManager = MetaManager.singleInstance();

    public SaveCommand parse(IdEntity object, SessionCtx sessionCtx) {
        return parse(object, sessionCtx, null);
    }

    /**
     * @param forcedType 强制指定的命令类型：
     *                   Insert——始终构造插入语句，实体已带 id 时保留该 id（用于指定主键的插入）；
     *                   Update——始终构造更新语句，要求 id 非空；
     *                   null——按默认约定：id 非空即 Update，id 为空即 Insert（自动生成主键）
     */
    public SaveCommand parse(IdEntity object, SessionCtx sessionCtx, CommandType forcedType) {
        EntityMeta entityMeta = metaManager.get(object.getClass());
        if (forcedType == CommandType.Update && Strings.isBlank(object.getId())) {
            throw new IllegalArgumentException("显式更新要求实体主键非空: " + entityMeta.getEntityName());
        }
        SaveCommand command = new SaveCommand();
        command.setEntityName(entityMeta.getEntityName());


        Map<String,Object> entity = new HashMap<>(entityMeta.getFieldMetas().size());
        try {
            for (FieldMeta fm : entityMeta.getFieldMetas()) {
                entity.put(fm.getFieldName(), PropertyUtils.getProperty(object, fm.getFieldName()));
            }
            String PK = entityMeta.getId().getFieldName();
            boolean asUpdate;
            if (forcedType != null) {
                asUpdate = (forcedType == CommandType.Update);
            } else {
                asUpdate = Strings.isNotBlank(object.getId());
            }
            if (asUpdate) {
                command.setCommandType(CommandType.Update);

                FilterGroup fg = new FilterGroup();
                fg.addFilter(PK, String.valueOf(entity.get(PK)));
                command.setWhere(fg);
                EntitySaveFieldValueFillRuntimeResolver.fillIfAvailable(new EntitySaveFieldValueFillContext(
                        entityMeta.getEntityName(),
                        CommandType.Update,
                        entityMeta,
                        metaManager.newDefaultEntityMap(entityMeta.getEntityName()),
                        entity,
                        sessionCtx,
                        object
                ));

                String[] updateFields = new String[entity.size()];
                entity.keySet().toArray(updateFields);
                command.setFields(updateFields);
                command.setValueMap(entity);
            } else {
                command.setCommandType(CommandType.Insert);
                if (Strings.isBlank(object.getId())) {
                    entity.put(PK, UIDGenerator.generate());
                }
                EntitySaveFieldValueFillRuntimeResolver.fillIfAvailable(new EntitySaveFieldValueFillContext(
                        entityMeta.getEntityName(),
                        CommandType.Insert,
                        entityMeta,
                        metaManager.newDefaultEntityMap(entityMeta.getEntityName()),
                        entity,
                        sessionCtx,
                        object
                ));
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

}
