package cn.geelato.web.platform.script.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author diabl
 */
@Getter
@Setter
@Entity(name = "platform_api_param")
@Title(title = "接口服务参数")
public class ApiParam extends BaseEntity {
    @Title(title = "所属应用")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "所属接口")
    @Col(name = "api_id")
    private String apiId;
    private String pid;
    @Col(name = "param_type")
    @Title(title = "参数类型")
    private String paramType;
    @Col(name = "body_type")
    @Title(title = "类型")
    private String bodyType;
    private String name;
    @Col(name = "data_type")
    @Title(title = "参数数据类型")
    private String dataType;
    @Col(name = "default_value")
    @Title(title = "参数默认值")
    private String defaultValue;
    @Col(name = "demo_value")
    @Title(title = "示例数据")
    private String demoValue;
    @Col(name = "alternate_type")
    @Title(title = "交互类型")
    private String alternateType;
    private boolean required = false;
    private String remark;
    @Transient
    private List<ApiParam> children;
}
