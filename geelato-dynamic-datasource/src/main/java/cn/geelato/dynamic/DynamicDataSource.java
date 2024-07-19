package cn.geelato.dynamic;

import lombok.Getter;
import lombok.Setter;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;

public class DynamicDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return DynamicDatasourceHolder.getDataSourceKey();
    }
}
