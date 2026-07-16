package cn.geelato.orm.executor.spi;

import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.orm.Dao;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * 基于 Dao 的默认执行策略。
 */
public class DaoMetaExecutionStrategy implements MetaExecutionStrategy {

    private final Dao dao;

    public DaoMetaExecutionStrategy(Dao dao) {
        this.dao = dao;
    }

    public Dao getDao() {
        return dao;
    }

    @Override
    public Map<String, Object> queryForMap(BoundSql boundSql) {
        return dao.queryForMap(boundSql);
    }

    @Override
    public <T> T queryForObject(BoundSql boundSql, Class<T> requiredType) {
        return dao.queryForObject(boundSql, requiredType);
    }

    @Override
    public List<Map<String, Object>> queryForMapList(BoundPageSql boundPageSql) {
        return dao.queryForMapList(boundPageSql);
    }

    @Override
    public long queryTotal(BoundPageSql boundPageSql) {
        Long total = dao.queryTotal(boundPageSql);
        return total != null ? total : 0L;
    }

    @Override
    public <T> List<T> queryForOneColumnList(BoundSql boundSql, Class<T> elementType) {
        return dao.queryForOneColumnList(boundSql, elementType);
    }

    @Override
    public Map<String, Object> callForMap(String callSql, Object[] params) {
        return dao.callForMap(callSql, params);
    }

    @Override
    public List<Map<String, Object>> callForMapList(String callSql, Object[] params) {
        return dao.callForMapList(callSql, params);
    }

    @Override
    public Map<String, Object> nativeQueryForMap(String sql, Object[] params) {
        return dao.nativeQueryForMap(sql, params);
    }

    @Override
    public List<Map<String, Object>> nativeQueryForMapList(String sql, Object[] params) {
        return dao.nativeQueryForMapList(sql, params);
    }

    @Override
    public <T> T nativeQueryForObject(String sql, Object[] params, Class<T> requiredType) {
        return dao.nativeQueryForObject(sql, params, requiredType);
    }

    @Override
    public int nativeExecute(String sql, Object[] params) {
        return dao.nativeExecute(sql, params);
    }

    @Override
    public String save(BoundSql boundSql) {
        return dao.save(boundSql);
    }

    @Override
    public List<String> multiSave(List<BoundSql> boundSqlList) {
        return dao.multiSave(boundSqlList);
    }

    @Override
    public int delete(BoundSql boundSql) {
        return dao.delete(boundSql);
    }

    @Override
    public DataSource getDataSource() {
        return dao.getJdbcTemplate() != null ? dao.getJdbcTemplate().getDataSource() : null;
    }
}
