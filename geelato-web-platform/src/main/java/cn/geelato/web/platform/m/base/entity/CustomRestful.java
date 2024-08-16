package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Setter;

/**
 * @author diabl
 */
@Setter
@Entity(name = "platform_restful")
@Title(title = "自定义接口编排")
public class CustomRestful extends BaseEntity {
    private String appId;
    private String title;
    private String keyName;
    private String parameterDefinition;
    private String description;
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    private String configType;
    private String encodingContent;

    @Col(name = "app_id")
    @Title(title = "所属应用")
    public String getAppId() {
        return appId;
    }

    @Col(name = "title")
    @Title(title = "标题")
    public String getTitle() {
        return title;
    }

    @Col(name = "key_name")
    @Title(title = "键名称")
    public String getKeyName() {
        return keyName;
    }

    @Col(name = "parameter_definition")
    @Title(title = "参数定义")
    public String getParameterDefinition() {
        return parameterDefinition;
    }

    @Col(name = "description")
    @Title(title = "描述")
    public String getDescription() {
        return description;
    }

    @Col(name = "enable_status")
    @Title(title = "是否启用")
    public int getEnableStatus() {
        return enableStatus;
    }

    @Col(name = "config_type")
    @Title(title = "配置类型")
    public String getConfigType() {
        return configType;
    }

    @Col(name = "encoding_content")
    @Title(title = "编码内容")
    public String getEncodingContent() {
        return encodingContent;
    }
}
