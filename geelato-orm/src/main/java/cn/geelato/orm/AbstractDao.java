package cn.geelato.orm;

import cn.geelato.orm.querydsl.PageQueryRequest;

import java.util.List;
import java.util.Map;

public abstract class AbstractDao {
    abstract <T> List<T> queryList();
    abstract <T> List<T> queryList(Class<T> entityType, FilterGroup filterGroup, String orderBy) ;
    abstract <T> List<T> queryList(Class<T> entityType, Map<String, Object> params, String orderBy);
    abstract <T> List<T> pageQueryList(Class<T> entityType, FilterGroup filterGroup, PageQueryRequest request);

    abstract <T> T queryForObject(BoundSql boundSql, Class<T> requiredType);

    abstract <T> T queryForMap();


//    abstract  <E extends IdEntity> Map save(E entity);
}
