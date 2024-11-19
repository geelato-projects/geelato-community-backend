package cn.geelato.core.sql;

import cn.geelato.core.AbstractManager;
import cn.geelato.core.SessionCtx;
import cn.geelato.core.gql.command.*;
import cn.geelato.core.gql.execute.BoundPageSql;
import cn.geelato.core.gql.execute.BoundSql;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.sql.provider.*;
import cn.geelato.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 基于元数据的sql语句管理器
 * <p>创建sql语句</p>
 *
 * @author geemeta
 */
@Slf4j
@SuppressWarnings("rawtypes")
public class SqlManager extends AbstractManager {
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DateUtils.DATETIME);
    private static SqlManager instance;
    private final MetaManager metaManager = MetaManager.singleInstance();
    private final MetaQuerySqlProvider metaQuerySqlProvider = new MetaQuerySqlProvider();
    private final MetaViewQuerySqlProvider metaViewQuerySqlProvider = new MetaViewQuerySqlProvider();
    private final MetaQuerySqlMultiProvider metaQuerySqlMultiProvider = new MetaQuerySqlMultiProvider();
    private final MetaQueryTreeSqlProvider metaQueryTreeSqlProvider = new MetaQueryTreeSqlProvider();
    private final MetaInsertSqlProvider metaInsertSqlProvider = new MetaInsertSqlProvider();
    private final MetaUpdateSqlProvider metaUpdateSqlProvider = new MetaUpdateSqlProvider();
    private final MetaDeleteSqlProvider metaDeleteSqlProvider = new MetaDeleteSqlProvider();

    public static SqlManager singleInstance() {
        lock.lock();
        if (instance == null) {
            instance = new SqlManager();
        }
        lock.unlock();
        return instance;
    }

    private SqlManager() {
        log.info("SqlManager Instancing...");
    }

    //========================================================
    //                  基于元数据  gql                      ==
    //========================================================
    public BoundSql generateQuerySql(QueryCommand command) {
        return metaQuerySqlProvider.generate(command);
    }

    public BoundPageSql generatePageQuerySql(QueryCommand command) {
        BoundPageSql boundPageSql = new BoundPageSql();
        boundPageSql.setBoundSql(metaQuerySqlProvider.generate(command));
        boundPageSql.setCountSql(metaQuerySqlProvider.buildCountSql(command));
        return boundPageSql;
    }

    public BoundPageSql generatePageQuerySql(QueryTreeCommand command) {
        BoundPageSql boundPageSql = new BoundPageSql();
        boundPageSql.setBoundSql(metaQueryTreeSqlProvider.generate(command));
        boundPageSql.setCountSql(metaQueryTreeSqlProvider.buildCountSql(command));
        return boundPageSql;
    }

    public BoundPageSql generatePageQuerySqlMulti(QueryCommand command) {
        BoundPageSql boundPageSql = new BoundPageSql();
        boundPageSql.setBoundSql(metaQuerySqlMultiProvider.generate(command));
        boundPageSql.setCountSql(metaQuerySqlMultiProvider.buildCountSql(command));
        return boundPageSql;
    }

    public BoundSql generateSaveSql(SaveCommand command) {
        if (command.getCommandType() == CommandType.Update) {
            return metaUpdateSqlProvider.generate(command);
        } else {
            return metaInsertSqlProvider.generate(command);
        }
    }

    public List<BoundSql> generateBatchSaveSql(List<SaveCommand> commandList) {
        List<BoundSql> boundSqlList = new ArrayList<>();
        for (SaveCommand saveCommand : commandList) {
            boundSqlList.add(generateSaveSql(saveCommand));
        }
        return boundSqlList;
    }

    public BoundSql generateDeleteSql(DeleteCommand command) {
        return metaDeleteSqlProvider.generate(command);
    }


    //========================================================
    //                  基于元数据  model                   ==
    //========================================================
    public <T> BoundSql generateQueryForObjectOrMapSql(Class<T> clazz, FilterGroup filterGroup, String orderBy) {
        return generateQuerySql(clazz, false, filterGroup, orderBy, null);
    }

    public <T> BoundSql generateQueryForListSql(Class<T> clazz, FilterGroup filterGroup, String orderBy) {
        return generateQuerySql(clazz, true, filterGroup, orderBy, null);
    }

    /**
     * @param clazz       查询的实体
     * @param filterGroup 过滤条件
     * @param field       指定实体中的查询列，单列
     * @return 单列列表查询语句
     */
    public <T> BoundSql generateQueryForListSql(Class<T> clazz, FilterGroup filterGroup, String orderBy, String field) {
        return generateQuerySql(clazz, true, filterGroup, orderBy, new String[]{field});
    }

    /**
     * @param clazz       查询的实体
     * @param filterGroup 过滤条件
     * @param fields      指定实体中的查询列，多列
     * @return 多列列表查询语句
     */
    public <T> BoundSql generateQueryForListSql(Class<T> clazz, FilterGroup filterGroup, String orderBy, String[] fields) {
        return generateQuerySql(clazz, true, filterGroup, orderBy, fields);
    }


    /**
     * @param clazz       查询的实体
     * @param isArray     是否查询多条记录，true：是，false：否
     * @param filterGroup 过滤条件
     * @param fields      指定实体中的查询列，多列
     * @return 多列列表查询语句
     */
    private <T> BoundSql generateQuerySql(Class<T> clazz, boolean isArray, FilterGroup filterGroup, String orderBy, String[] fields) {
        QueryCommand queryCommand = new QueryCommand();
        EntityMeta em = metaManager.get(clazz);
        queryCommand.setEntityName(em.getEntityName());
        queryCommand.setFields(fields != null && fields.length > 0 ? fields : em.getFieldNames());
        queryCommand.setQueryForList(isArray);
        queryCommand.setWhere(filterGroup);
        queryCommand.setOrderBy(orderBy);
        return metaQuerySqlProvider.generate(queryCommand);
    }
    public <T> BoundSql generatePageQuerySql(QueryCommand queryCommand, Class<T> clazz, boolean isArray, FilterGroup filterGroup, String[] fields) {
        EntityMeta em = metaManager.get(clazz);
        queryCommand.setEntityName(em.getEntityName());
        queryCommand.setFields(fields != null && fields.length > 0 ? fields : em.getFieldNames());
        queryCommand.setQueryForList(isArray);
        queryCommand.setWhere(filterGroup);
        return metaQuerySqlProvider.generate(queryCommand);
    }
    /**
     * 删除服务
     */
    public BoundSql generateDeleteSql(Class clazz, FilterGroup filterGroup) {
        DeleteCommand deleteCommand = new DeleteCommand();
        EntityMeta em = metaManager.get(clazz);
        deleteCommand.setEntityName(em.getEntityName());
        deleteCommand.setFields(em.getFieldNames());
        deleteCommand.setWhere(filterGroup);
        return metaDeleteSqlProvider.generate(deleteCommand);
    }

    public BoundSql generateDeleteSql(String entityName, FilterGroup filterGroup) {
        CommandValidator validator = new CommandValidator();
        Assert.isTrue(validator.validateEntity(entityName), validator.getMessage());
        return generateDeleteSql(entityName, filterGroup, validator);
    }

    private BoundSql generateDeleteSql(String entityName, FilterGroup filterGroup, CommandValidator validator) {
        DeleteCommand deleteCommand = new DeleteCommand();
        EntityMeta em = metaManager.getByEntityName(entityName);
        deleteCommand.setEntityName(em.getEntityName());
        Map<String, Object> params = new HashMap<>();
        String newDataString = simpleDateFormat.format(new Date());
        if (validator.hasKeyField("delStatus")) {
            params.put("delStatus", 1);
        }
        if (validator.hasKeyField("deleteAt")) {
            params.put("deleteAt", newDataString);
        }
        if (validator.hasKeyField("updateAt")) {
            params.put("updateAt", newDataString);
        }
        if (validator.hasKeyField("updater")) {
            params.put("updater", SessionCtx.getCurrentUser().getUserId());
        }
        if (validator.hasKeyField("updaterName")) {
            params.put("updaterName", SessionCtx.getCurrentUser().getUserName());
        }
        String[] updateFields = new String[params.keySet().size()];
        params.keySet().toArray(updateFields);
        deleteCommand.setFields(updateFields);
        deleteCommand.setValueMap(params);
        deleteCommand.setWhere(filterGroup);
        return metaDeleteSqlProvider.generate(deleteCommand);
    }



    public <T> BoundSql generatePageQuerySql(QueryViewCommand queryCommand, String entityName, boolean isArray, FilterGroup filterGroup, String[] fields) {
        EntityMeta em = metaManager.getByEntityName(entityName);
        queryCommand.setEntityName(em.getEntityName());
        queryCommand.setFields(fields != null && fields.length > 0 ? fields : em.getFieldNames());
        queryCommand.setQueryForList(isArray);
        queryCommand.setWhere(filterGroup);
        return metaViewQuerySqlProvider.generate(queryCommand);
    }
}