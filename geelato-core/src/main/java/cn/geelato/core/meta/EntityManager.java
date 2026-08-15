package cn.geelato.core.meta;

import cn.geelato.core.AbstractManager;
import cn.geelato.core.SessionCtx;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.meta.model.entity.IdEntity;
import cn.geelato.core.meta.model.parser.EntitySaveParser;
import cn.geelato.core.sql.provider.MetaInsertSqlProvider;
import cn.geelato.core.sql.provider.MetaUpdateSqlProvider;

/**
 * @author geemeta
 */
public class EntityManager extends AbstractManager {
    private static EntityManager instance;
    private final EntitySaveParser entitySaveParser = new EntitySaveParser();
    private final MetaInsertSqlProvider metaInsertSqlProvider = new MetaInsertSqlProvider();
    private final MetaUpdateSqlProvider metaUpdateSqlProvider = new MetaUpdateSqlProvider();

    public static EntityManager singleInstance() {
        lock.lock();
        if (instance == null) {
            instance = new EntityManager();
        }
        lock.unlock();
        return instance;
    }

    public BoundSql generateSaveSql(IdEntity entity, SessionCtx sessionCtx) {
        return generateSaveSql(entity, sessionCtx, null);
    }

    /**
     * @param forcedType 强制指定的命令类型（Insert/Update），为 null 时按实体是否有 id 自动判定
     * @see EntitySaveParser#parse(IdEntity, SessionCtx, CommandType)
     */
    public BoundSql generateSaveSql(IdEntity entity, SessionCtx sessionCtx, CommandType forcedType) {
        SaveCommand command = entitySaveParser.parse(entity, sessionCtx, forcedType);
        if (command.getCommandType() == CommandType.Update) {
            return metaUpdateSqlProvider.generate(command);
        } else {
            return metaInsertSqlProvider.generate(command);
        }
    }
}
