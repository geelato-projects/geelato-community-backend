package cn.geelato.core.meta.model.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Title;
import cn.geelato.utils.DateUtils;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 基础实体，默认一些人员信息、更新信息等常规字段
 */
@Getter
@Setter
public class BaseEntity extends IdEntity {

    @Title(title = "创建时间")
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    @Col(name = "create_at", nullable = false)
    @TableField(fill = FieldFill.INSERT)
    private Date createAt;

    @Title(title = "创建者")
    @Col(name = "creator", nullable = false)
    @TableField(fill = FieldFill.INSERT)
    private String creator;

    @Title(title = "创建者名称")
    @Col(name = "creator_name")
    @TableField(fill = FieldFill.INSERT)
    private String creatorName;

    @Title(title = "单位", description = "bu即business unit，记录（分）公司的编码信息，可用于分公司、或事业部，主要用于数据权限的区分，如分公司可看自己分公司的数据。")
    @Col(name = "bu_id", charMaxlength = 32)
    @TableField(fill = FieldFill.INSERT)
    private String buId;

    @Title(title = "部门")
    @Col(name = "dept_id", charMaxlength = 32)
    @TableField(fill = FieldFill.INSERT)
    private String deptId;

    @Title(title = "租户编码")
    @Col(name = "tenant_code", charMaxlength = 32)
    @TableField(fill = FieldFill.INSERT)
    private String tenantCode;

    @Title(title = "更新时间")
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    @Col(name = "update_at", nullable = false)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateAt;
    @Title(title = "更新者")
    @Col(name = "updater", nullable = false)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;

    @Title(title = "更新者名称")
    @Col(name = "updater_name")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updaterName;

    @Title(title = "删除时间")
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    @Col(name = "delete_at")
    private Date deleteAt;
    @Title(title = "删除状态", description = "逻辑删除的状态，1：已删除、0：未删除")
    @Col(name = "del_status")
    private int delStatus = ColumnDefault.DEL_STATUS_VALUE;

    /***
     * 其它属性设置之后，调用。可用于通用的增删改查功能中，特别字段的生成
     */
    public void afterSet() {
    }
}
