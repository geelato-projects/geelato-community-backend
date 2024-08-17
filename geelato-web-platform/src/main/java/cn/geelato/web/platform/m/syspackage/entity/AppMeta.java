package cn.geelato.web.platform.m.syspackage.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Getter
@Setter
public class AppMeta {

    private String metaName;
    private Object metaData;

    public AppMeta(String metaName, Object metaData) {
        this.metaName = metaName;
        this.metaData = metaData;
    }
}
