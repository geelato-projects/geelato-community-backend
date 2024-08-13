package cn.geelato.web.platform.script.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;

import java.util.List;

@Entity(name = "platform_api", table = "platform_api")
@Title(title = "服务接口")
public class Api extends BaseEntity {
    private String appId;
    private String name;
    private String code;
    private String remark;
    private Integer version;
    private String groupName;
    private int enableStatus = 1;
    private String sourceContent;
    private String releaseContent;
    private List<ApiParam> parameters;

    @Col(name = "app_id")
    @Title(title = "所属应用")
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Col(name = "name")
    @Title(title = "名称")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Col(name = "code")
    @Title(title = "接口编码")
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Col(name = "remark")
    @Title(title = "备注")
    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Col(name = "version")
    @Title(title = "版本号")
    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Col(name = "group_name")
    @Title(title = "分组名称")
    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    @Col(name = "enable_status")
    @Title(title = "是否启用。0：禁用；1：启用。用于控制是否可以访问该接口服务。")
    public int getEnableStatus() {
        return enableStatus;
    }

    public void setEnableStatus(int enableStatus) {
        this.enableStatus = enableStatus;
    }

    @Col(name = "source_content")
    @Title(title = "对于GlPage的组件树字符串")
    public String getSourceContent() {
        return sourceContent;
    }

    public void setSourceContent(String sourceContent) {
        this.sourceContent = sourceContent;
    }

    @Col(name = "release_content")
    @Title(title = "生成的可执行javascript脚本")
    public String getReleaseContent() {
        return releaseContent;
    }

    public void setReleaseContent(String releaseContent) {
        this.releaseContent = releaseContent;
    }

    @Transient
    public List<ApiParam> getParameters() {
        return parameters;
    }

    public void setParameters(List<ApiParam> parameters) {
        this.parameters = parameters;
    }
}
