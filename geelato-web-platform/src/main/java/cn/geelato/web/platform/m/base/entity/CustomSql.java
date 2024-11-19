package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.constants.MediaTypes;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.utils.Base64Utils;
import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Getter
@Setter
@Entity(name = "platform_sql")
@Title(title = "自定义接口编排")
public class CustomSql extends BaseEntity {
    @Title(title = "所属应用")
    @Col(name = "app_id")
    private String appId;
    private String title;
    @Title(title = "键名称")
    @Col(name = "key_name")
    private String keyName;
    @Title(title = "请求参数")
    @Col(name = "request_params")
    private String requestParams;
    @Title(title = "响应参数类型")
    @Col(name = "response_type")
    private String responseType;
    @Title(title = "响应参数")
    @Col(name = "response_params")
    private String responseParams;
    private String description;
    @Title(title = "是否启用")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    @Title(title = "配置类型")
    @Col(name = "config_type")
    private String configType;
    @Title(title = "编码内容")
    @Col(name = "encoding_content")
    private String encodingContent;

    @Override
    public void afterSet() {
        if (Base64Utils.isBase64(this.getEncodingContent(), MediaTypes.TEXT_PLAIN_BASE64)) {
            this.setEncodingContent(Base64Utils.decode(this.getEncodingContent()));
        }
    }
}
