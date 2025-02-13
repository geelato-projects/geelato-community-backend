package cn.geelato.web.platform.boot;

import cn.geelato.core.ds.DataSourceManager;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;


@SuppressWarnings("rawtypes")
public class DynamicDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        String dataSourceKey=DynamicDatasourceHolder.getDataSourceKey();
        if(dataSourceKey!=null){
            if(getResolvedDataSources().get(dataSourceKey)==null){
                Object lazyDataSource= DataSourceManager.singleInstance().getLazyDataSource(dataSourceKey);
                DataSource dataSource=DataSourceManager.singleInstance().buildDataSource((Map) lazyDataSource);
                Map<Object, DataSource> originTargetDataSources=this.getResolvedDataSources();
                Map<Object, Object> targetDataSources = new HashMap<>(originTargetDataSources);
                targetDataSources.put(dataSourceKey,dataSource);
                setTargetDataSources(targetDataSources);
                super.afterPropertiesSet();
            }
        }
        return dataSourceKey;
    }
}
