package cn.geelato.orm.meta;

import org.geelato.core.AbstractManager;
import org.geelato.core.Ctx;
import org.geelato.core.gql.execute.BoundSql;
import org.geelato.core.gql.parser.CommandType;
import org.geelato.core.gql.parser.SaveCommand;
import org.geelato.core.meta.model.entity.IdEntity;
import org.geelato.core.meta.model.parser.EntitySaveParser;
import org.geelato.core.sql.provider.MetaDeleteSqlProvider;
import org.geelato.core.sql.provider.MetaInsertSqlProvider;
import org.geelato.core.sql.provider.MetaQuerySqlProvider;
import org.geelato.core.sql.provider.MetaUpdateSqlProvider;

/**
 * @author geemeta
 */
public class EntityManager extends AbstractManager {
    private static EntityManager instance;
    private final EntitySaveParser entitySaveParser = new EntitySaveParser();
    private final MetaQuerySqlProvider metaQuerySqlProvider = new MetaQuerySqlProvider();
    private final MetaInsertSqlProvider metaInsertSqlProvider = new MetaInsertSqlProvider();
    private final MetaUpdateSqlProvider metaUpdateSqlProvider = new MetaUpdateSqlProvider();
    private final MetaDeleteSqlProvider metaDeleteSqlProvider = new MetaDeleteSqlProvider();

    public static EntityManager singleInstance() {
        lock.lock();
        if (instance == null) {
            instance = new EntityManager();
        }
        lock.unlock();
        return instance;
    }

    public BoundSql generateSaveSql(IdEntity entity, Ctx ctx) {
        SaveCommand command = entitySaveParser.parse(entity, ctx);
        if (command.getCommandType() == CommandType.Update) {
            return metaUpdateSqlProvider.generate(command);
        } else {
            return metaInsertSqlProvider.generate(command);
        }
    }
}
