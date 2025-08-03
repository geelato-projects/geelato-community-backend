package cn.geelato.web.platform.m.excel.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import cn.geelato.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 * 导出文件模板
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

    @Title(title = "状态")
    private String template2;
    @Title(title = "状态")
    private String template3;
    @Title(title = "状态")
    private String template4;
    @Title(title = "状态")
    private String template5;
    @Title(title = "状态")
    private String template6;
    @Title(title = "状态")
    private String template7;
    @Title(title = "状态")
    private String template8;
    @Title(title = "状态")
    private String template9;

    /**
     * 根据索引获取对应的模板字符串
     *
     * @param index 索引值，范围从1到9
     * @return 根据索引返回的模板字符串
     * @throws RuntimeException 如果索引超出范围，则抛出异常
     */
    public String indexTemplate(String index) {
        return switch (index) {
            case "1" -> this.template;
            case "2" -> this.template2;
            case "3" -> this.template3;
            case "4" -> this.template4;
            case "5" -> this.template5;
            case "6" -> this.template6;
            case "7" -> this.template7;
            case "8" -> this.template8;
            case "9" -> this.template9;
            default -> throw new RuntimeException("模板索引超出范围！");
        };
    }

    public String indexTemplateDefault(String index) {
        index = StringUtils.isBlank(index) ? "1" : index;
        return indexTemplate(index);
    }
}
