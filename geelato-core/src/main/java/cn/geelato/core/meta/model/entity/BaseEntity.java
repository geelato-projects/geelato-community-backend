package cn.geelato.core.meta.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Title;

import java.util.Date;

/**
 * 基础实体，默认一些人员信息、更新信息等常规字段
 */
public class BaseEntity extends IdEntity {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date deleteAt;
    private String creator;
    private String creatorName;
    private String updater;
    private String updaterName;

    // 逻辑删除的标识
    private int delStatus = ColumnDefault.DEL_STATUS_VALUE;
    // 单位Id
    private String buId;
    // 部门Id
    private String deptId;
    // 租户编码
    private String tenantCode;

    public BaseEntity() {
    }

    public BaseEntity(String Id) {
        setId(id);
    }

    @Col(name = "create_at", nullable = false)
    @Title(title = "创建时间")
    public Date getCreateAt() {
        return createAt;
    }

    public void setCreateAt(Date createAt) {
        this.createAt = createAt;
    }

    @Col(name = "update_at", nullable = false)
    @Title(title = "更新时间")
    public Date getUpdateAt() {
        return updateAt;
    }

    public void setUpdateAt(Date updateAt) {
        this.updateAt = updateAt;
    }

    @Col(name = "delete_at", nullable = true)
    @Title(title = "删除时间")
    public Date getDeleteAt() {
        return deleteAt;
    }

    public void setDeleteAt(Date deleteAt) {
        this.deleteAt = deleteAt;
    }

    @Col(name = "creator", nullable = false)
    @Title(title = "创建者")
    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    @Col(name = "creator_name", nullable = true)
    @Title(title = "创建者名称")
    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    @Col(name = "updater", nullable = false)
    @Title(title = "更新者")
    public String getUpdater() {
        return updater;
    }

    public void setUpdater(String updater) {
        this.updater = updater;
    }

    @Col(name = "updater_name", nullable = true)
    @Title(title = "更新者名称")
    public String getUpdaterName() {
        return updaterName;
    }

    public void setUpdaterName(String updaterName) {
        this.updaterName = updaterName;
    }

    @Col(name = "del_status")
    @Title(title = "删除状态", description = "逻辑删除的状态，1：已删除、0：未删除")
    public int getDelStatus() {
        return delStatus;
    }

    public void setDelStatus(int delStatus) {
        this.delStatus = delStatus;
    }


    @Col(name = "bu_id", nullable = true, charMaxlength = 32)
    @Title(title = "单位", description = "bu即business unit，记录（分）公司的编码信息，可用于分公司、或事业部，主要用于数据权限的区分，如分公司可看自己分公司的数据。")
    public String getBuId() {
        return buId;
    }

    public void setBuId(String buId) {
        this.buId = buId;
    }

    @Col(name = "dept_id", nullable = true, charMaxlength = 32)
    @Title(title = "部门")
    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    @Col(name = "tenant_code", nullable = true, charMaxlength = 64)
    @Title(title = "租户编码")
    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    /***
     * 其它属性设置之后，调用。可用于通用的增删改查功能中，特别字段的生成
     */
    public void afterSet() {

    }
}
