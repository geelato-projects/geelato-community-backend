package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Setter
@Getter
public class Tenant {
    private String code;
    private String name;
    private Map<String, Object> configuration;
    private Map<String,Object> properties;

    public Tenant(String code) {
        this.code = code;
        initConfigAndProperties();
    }

    public Tenant(String code, String name) {
        this.code = code;
        this.name = name;
        initConfigAndProperties();
    }

    private void initConfigAndProperties() {
        //todo
        this.configuration = new HashMap<>();
        this.properties = new HashMap<>();
    }
}
