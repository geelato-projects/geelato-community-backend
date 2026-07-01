package cn.geelato.web.platform.boot.properties;

import lombok.Data;

@Data
public class PrimaryDataSourceProperties {
    private String url;
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;
}

