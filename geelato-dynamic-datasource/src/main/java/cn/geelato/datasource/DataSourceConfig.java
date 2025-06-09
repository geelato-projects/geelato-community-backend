package cn.geelato.datasource;

import lombok.Getter;
import lombok.Setter;

/**
 * 数据源配置实体类
 */
@Setter
@Getter
public class DataSourceConfig {
    private String key;
    private String name;
    private String url;
    private String username;
    private String password;
    private String driverClassName;
    private boolean enableStatus = true;
    
    public DataSourceConfig() {}
    
    public DataSourceConfig(String key, String url, String username, String password) {
        this.key = key;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public String toString() {
        return "DataSourceConfig{" +
                "key='" + key + '\'' +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", username='" + username + '\'' +
                ", driverClassName='" + driverClassName + '\'' +
                ", enableStatus=" + enableStatus +
                '}';
    }
}