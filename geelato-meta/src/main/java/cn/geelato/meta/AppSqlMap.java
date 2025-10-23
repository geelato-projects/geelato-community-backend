package cn.geelato.meta;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Getter
@Setter
@Entity(name = "platform_app_r_sql",catalog = "platform")
@Title(title = "应用接口编码关系表")
public class AppSqlMap extends BaseEntity {
    @Col(name = "app_id")
    @Title(title = "申请应用主键")
    private String appId;
    @Col(name = "app_name")
    @Title(title = "申请应用名称")
    private String appName;
    @Col(name = "sql_id")
    @Title(title = "接口编排主键")
    private String sqlId;
    @Col(name = "sql_title")
    @Title(title = "接口编排标题")
    private String sqlTitle;
    @Col(name = "sql_key")
    @Title(title = "接口编排键名称")
    private String sqlKey;
    @Col(name = "sql_app_id")
    @Title(title = "接口编排所属应用")
    private String sqlAppId;
    @Col(name = "approval_need")
    @Title(title = "是否需要审批")
    private boolean approvalNeed = false;
    @Col(name = "approval_status")
    @Title(title = "审批状态")
    private String approvalStatus;
    @Col(name = "enable_status")
    @Title(title = "是否启用")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    @Title(title = "描述")
    private String description;
}
