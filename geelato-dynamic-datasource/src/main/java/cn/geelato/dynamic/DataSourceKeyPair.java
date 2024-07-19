package cn.geelato.dynamic;

import lombok.Getter;
import lombok.Setter;

import javax.sql.DataSource;

@Getter
@Setter
public class DataSourceKeyPair<T extends DataSource> {
    private String datasourceKey;
    private T datasource;
}
