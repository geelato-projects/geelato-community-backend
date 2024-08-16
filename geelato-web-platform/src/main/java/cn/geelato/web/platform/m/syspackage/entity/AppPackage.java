package cn.geelato.web.platform.m.syspackage.entity;

import java.util.List;

public class AppPackage {

    private String sourceAppId;
    private String targetAppId;

    private String version;

    private String appCode;

    private List<AppMeta> appMetaList;

    private List<AppResource> appResourceList;

    public List<AppMeta> getAppMetaList() {
        return appMetaList;
    }

    public void setAppMetaList(List<AppMeta> appMetaList) {
        this.appMetaList = appMetaList;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getSourceAppId() {
        return sourceAppId;
    }

    public void setSourceAppId(String sourceAppId) {
        this.sourceAppId = sourceAppId;
    }

    public String getTargetAppId() {
        return targetAppId;
    }

    public void setTargetAppId(String targetAppId) {
        this.targetAppId = targetAppId;
    }

    public List<AppResource> getAppResourceList() {
        return appResourceList;
    }

    public void setAppResourceList(List<AppResource> appResourceList) {
        this.appResourceList = appResourceList;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
