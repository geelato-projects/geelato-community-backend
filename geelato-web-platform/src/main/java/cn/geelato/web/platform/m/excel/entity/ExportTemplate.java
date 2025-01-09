package cn.geelato.web.platform.m.excel.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.util.HashMap;
import java.util.Map;

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
     * 根据模板ID生成一个包含模板ID和索引号的映射
     *
     * @param templateId 模板ID，可能包含逗号分隔的模板ID和索引号
     * @return 包含模板ID和索引号的映射
     */
    public static Map<String, String> indexMap(String templateId) {
        Map<String, String> result = new HashMap<>();
        result.put("id", templateId);
        result.put("index", "1");
        if (templateId.indexOf(",") != -1) {
            try {
                String split[] = templateId.split(",");
                if (split.length == 2 && Strings.isNotBlank(split[0]) && Strings.isNotBlank(split[1])) {
                    result.put("id", split[0]);
                    int index = Integer.parseInt(split[1]);
                    if (index >= 1 && index <= 9) {
                        result.put("index", split[1]);
                    }
                }
            } catch (Exception e) {
                result.put("index", "1");
            }
        }
        return result;
    }

    /**
     * 根据索引获取对应的模板字符串
     *
     * @param index 索引值，范围从1到9
     * @return 根据索引返回的模板字符串
     * @throws RuntimeException 如果索引超出范围，则抛出异常
     */
    public String indexTemplate(String index) {
        switch (index) {
            case "1":
                return this.template;
            case "2":
                return this.template2;
            case "3":
                return this.template3;
            case "4":
                return this.template4;
            case "5":
                return this.template5;
            case "6":
                return this.template6;
            case "7":
                return this.template7;
            case "8":
                return this.template8;
            case "9":
                return this.template9;
            default:
                throw new RuntimeException("模板索引超出范围！");
        }
    }
}
