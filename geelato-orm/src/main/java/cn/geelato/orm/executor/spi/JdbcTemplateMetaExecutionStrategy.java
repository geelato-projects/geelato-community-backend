package cn.geelato.orm.executor.spi;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.orm.TransactionHelper;
import cn.geelato.core.orm.event.DeleteEventContext;
import cn.geelato.core.orm.event.DeleteEventManager;
import cn.geelato.core.orm.event.SaveEventContext;
import cn.geelato.core.orm.event.SaveEventManager;
import cn.geelato.orm.executor.support.BoundSqlJdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 JdbcTemplate 的直接执行策略。
 */
public class JdbcTemplateMetaExecutionStrategy implements MetaExecutionStrategy {

    private final JdbcTemplate jdbcTemplate;
    private final BoundSqlJdbcSupport jdbcSupport;
    private final Dao eventDao;

    public JdbcTemplateMetaExecutionStrategy(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcSupport = new BoundSqlJdbcSupport(jdbcTemplate);
        this.eventDao = new Dao(jdbcTemplate);
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    @Override
    public Map<String, Object> queryForMap(BoundSql boundSql) {
        return jdbcSupport.queryForMap(boundSql);
    }

    @Override
    public <T> T queryForObject(BoundSql boundSql, Class<T> requiredType) {
        return jdbcSupport.queryForObject(boundSql, requiredType);
    }

    @Override
    public List<Map<String, Object>> queryForMapList(BoundPageSql boundPageSql) {
        return jdbcSupport.queryForMapList(boundPageSql);
    }

    @Override
    public long queryTotal(BoundPageSql boundPageSql) {
        Long total = jdbcSupport.queryTotal(boundPageSql);
        return total != null ? total : 0L;
    }

    @Override
    public <T> List<T> queryForOneColumnList(BoundSql boundSql, Class<T> elementType) {
        return jdbcSupport.queryForOneColumnList(boundSql, elementType);
    }

    @Override
    public Map<String, Object> callForMap(String callSql, Object[] params) {
        return jdbcSupport.callForMap(callSql, params);
    }

    @Override
    public List<Map<String, Object>> callForMapList(String callSql, Object[] params) {
        return jdbcSupport.callForMapList(callSql, params);
    }

    @Override
    public Map<String, Object> nativeQueryForMap(String sql, Object[] params) {
        return jdbcSupport.nativeQueryForMap(sql, params);
    }

    @Override
    public List<Map<String, Object>> nativeQueryForMapList(String sql, Object[] params) {
        return jdbcSupport.nativeQueryForMapList(sql, params);
    }

    @Override
    public <T> T nativeQueryForObject(String sql, Object[] params, Class<T> requiredType) {
        return jdbcSupport.nativeQueryForObject(sql, params, requiredType);
    }

    @Override
    public int nativeExecute(String sql, Object[] params) {
        return jdbcSupport.nativeExecute(sql, params);
    }

    @Override
    public String save(BoundSql boundSql) {
        SaveCommand command = (SaveCommand) boundSql.getCommand();
        SessionCtx sessionCtx = new SessionCtx();
        SaveEventContext context = new SaveEventContext(eventDao, sessionCtx, null, boundSql, command);
        SaveEventManager.fireBefore(context);
        jdbcSupport.executeUpdate(context.getBoundSql());
        context.setResultValueMap(command.getValueMap());
        SaveEventManager.fireAfter(context);
        return command.getPK();
    }

    @Override
    public List<String> multiSave(List<BoundSql> boundSqlList) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(getDataSource());
        TransactionStatus status = TransactionHelper.beginTransaction(transactionManager);
        List<String> returnPks = new ArrayList<>();
        try {
            for (BoundSql boundSql : boundSqlList) {
                SaveCommand command = (SaveCommand) boundSql.getCommand();
                SessionCtx sessionCtx = new SessionCtx();
                SaveEventContext context = new SaveEventContext(eventDao, sessionCtx, null, boundSql, command);
                SaveEventManager.fireBefore(context);
                jdbcSupport.executeUpdate(context.getBoundSql());
                context.setResultValueMap(command.getValueMap());
                SaveEventManager.fireAfter(context);
                returnPks.add(command.getPK());
            }
            TransactionHelper.commitTransaction(transactionManager, status);
            return returnPks;
        } catch (RuntimeException ex) {
            if (!status.isCompleted()) {
                TransactionHelper.rollbackTransaction(transactionManager, status);
            }
            throw ex;
        }
    }

    @Override
    public int delete(BoundSql boundSql) {
        DeleteCommand command = (DeleteCommand) boundSql.getCommand();
        SessionCtx sessionCtx = new SessionCtx();
        DeleteEventContext context = new DeleteEventContext(eventDao, sessionCtx, boundSql, command);
        DeleteEventManager.fireBefore(context);
        int affectedRows = jdbcSupport.executeUpdate(context.getBoundSql());
        context.setAffectedRows(affectedRows);
        DeleteEventManager.fireAfter(context);
        return affectedRows;
    }

    @Override
    public DataSource getDataSource() {
        return jdbcTemplate.getDataSource();
    }
}
