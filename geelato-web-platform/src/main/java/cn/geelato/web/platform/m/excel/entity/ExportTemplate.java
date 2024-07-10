package cn.geelato.web.platform.m.excel.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;

/**
 * @author diabl
 * @description: 导出文件模板
 * @date 2023/8/11 11:26
 */
@Entity(name = "platform_export_template")
@Title(title = "导出文件模板")
public class ExportTemplate extends BaseEntity implements EntityEnableAble {

    private String appId;
    private String title;
    private String useType;
    private String fileType;
    private String fileCode;
    private String template;
    private String templateRule;
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    private String description;
    private String businessTypeData;// 数据类型
    private String businessRuleData;// 清洗规则
    private String businessMetaData;// 元数据

    @Title(title = "状态")
    @Col(name = "app_id")
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Title(title = "状态")
    @Col(name = "title")
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Title(title = "用途")
    @Col(name = "use_type")
    public String getUseType() {
        return useType;
    }

    public void setUseType(String useType) {
        this.useType = useType;
    }

    @Title(title = "状态")
    @Col(name = "file_type")
    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    @Title(title = "状态")
    @Col(name = "file_code")
    public String getFileCode() {
        return fileCode;
    }

    public void setFileCode(String fileCode) {
        this.fileCode = fileCode;
    }

    @Title(title = "状态")
    @Col(name = "template")
    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    @Title(title = "状态")
    @Col(name = "template_rule")
    public String getTemplateRule() {
        return templateRule;
    }

    public void setTemplateRule(String templateRule) {
        this.templateRule = templateRule;
    }

    @Title(title = "状态")
    @Col(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    @Title(title = "状态", description = "0:停用|1:启用")
    @Col(name = "enable_status")
    public int getEnableStatus() {
        return enableStatus;
    }

    @Override
    public void setEnableStatus(int enableStatus) {
        this.enableStatus = enableStatus;
    }

    @Title(title = "数据类型")
    @Col(name = "business_type_data")
    public String getBusinessTypeData() {
        return businessTypeData;
    }

    public void setBusinessTypeData(String businessTypeData) {
        this.businessTypeData = businessTypeData;
    }

    @Title(title = "清洗规则")
    @Col(name = "business_rule_data")
    public String getBusinessRuleData() {
        return businessRuleData;
    }

    public void setBusinessRuleData(String businessRuleData) {
        this.businessRuleData = businessRuleData;
    }

    @Title(title = "元数据")
    @Col(name = "business_meta_data")
    public String getBusinessMetaData() {
        return businessMetaData;
    }

    public void setBusinessMetaData(String businessMetaData) {
        this.businessMetaData = businessMetaData;
    }
}
