package cn.geelato.web.platform.m.syspackage.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author diabl
 */
@Getter
@Setter
public class AppPackage {

    private String sourceAppId;
    private String targetAppId;
    private String version;
    private String appCode;
    private List<AppMeta> appMetaList;
    private List<AppResource> appResourceList;
}
