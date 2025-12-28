package cn.geelato.core.meta;

import cn.geelato.core.AbstractManager;
import cn.geelato.core.SessionCtx;
import cn.geelato.core.gql.execute.BoundSql;
import cn.geelato.core.gql.command.CommandType;
import cn.geelato.core.gql.command.SaveCommand;
import cn.geelato.core.meta.model.entity.IdEntity;
import cn.geelato.core.meta.model.parser.EntitySaveParser;
import cn.geelato.core.sql.provider.MetaDeleteSqlProvider;
import cn.geelato.core.sql.provider.MetaInsertSqlProvider;
import cn.geelato.core.sql.provider.MetaQuerySqlProvider;
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
        SaveCommand command = entitySaveParser.parse(entity, sessionCtx);
        if (command.getCommandType() == CommandType.Update) {
            return metaUpdateSqlProvider.generate(command);
        } else {
            return metaInsertSqlProvider.generate(command);
        }
    }
}
