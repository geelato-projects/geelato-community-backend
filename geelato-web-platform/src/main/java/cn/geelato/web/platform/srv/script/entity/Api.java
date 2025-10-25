package cn.geelato.web.platform.srv.script.entity;


import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.lang.meta.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author diabl
 */
@Getter
@Setter
@Entity(name = "platform_api",catalog = "platform")
@Title(title = "服务接口")
public class Api extends BaseEntity {
    @Title(title = "所属应用")
    @Col(name = "app_id")
    private String appId;
    private String name;
    private String code;
    @Title(title = "是否分页")
    private boolean paging = false;
    private String method;
    private String remark;
    private Integer version;
    @Title(title = "是否匿名访问", description = "1=允许匿名，不鉴权；0=不允许匿名，鉴权。")
    private int anonymous = 0;
    @Title(title = "分组名称")
    @Col(name = "group_name")
    private String groupName;
    @Title(title = "是否启用。0：禁用；1：启用。用于控制是否可以访问该接口服务。")
    @Col(name = "enable_status")
    private int enableStatus = 1;
    @Title(title = "对于GlPage的组件树字符串")
    @Col(name = "source_content")
    private String sourceContent;
    @Title(title = "生成的可执行javascript脚本")
    @Col(name = "release_content")
    private String releaseContent;
    @Title(title = "第三方访问地址")
    @Col(name = "outside_url")
    private String outsideUrl;
    @Title(title = "第三方访问状态")
    @Col(name = "outside_status")
    private int outsideStatus = 0;
    @Title(title = "响应格式")
    @Col(name = "response_format")
    private String responseFormat;
    @Title(title = "响应参数类型")
    @Col(name = "response_type")
    private String responseType;
    @Title(title = "日志级别")
    @Col(name = "log_level")
    private String logLevel;
    @Title(title = "请求参数")
    @Transient
    private List<ApiParam> requestParams;
    @Title(title = "响应参数")
    @Transient
    private List<ApiParam> responseParams;
}
