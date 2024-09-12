package cn.geelato.web.platform.m.excel.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 * @description: 导出文件模板
 */
@Getter
@Setter
@Entity(name = "platform_export_template")
@Title(title = "导出文件模板")
public class ExportTemplate extends BaseEntity implements EntityEnableAble {
    @Title(title = "状态")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "状态")
    private String title;
    @Title(title = "用途")
    @Col(name = "use_type")
    private String useType;
    @Title(title = "状态")
    @Col(name = "file_type")
    private String fileType;
    @Title(title = "状态")
    @Col(name = "file_code")
    private String fileCode;
    @Title(title = "状态")
    private String template;
    @Title(title = "状态")
    @Col(name = "template_rule")
    private String templateRule;
    @Title(title = "状态", description = "0:停用|1:启用")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    @Title(title = "状态")
    private String description;
    @Title(title = "数据类型")
    @Col(name = "business_type_data")
    private String businessTypeData;// 数据类型
    @Title(title = "清洗规则")
    @Col(name = "business_rule_data")
    private String businessRuleData;// 清洗规则
    @Title(title = "元数据")
    @Col(name = "business_meta_data")
    private String businessMetaData;// 元数据
}
