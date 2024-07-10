package cn.geelato.web.platform.m.excel.entity;

import org.apache.logging.log4j.util.Strings;
import cn.geelato.web.platform.enums.ExcelColumnTypeEnum;
import cn.geelato.web.platform.enums.ExcelMultiSceneTypeEnum;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author diabl
 * @description: 导入业务数据，每列数据的类型
 * @date 2023/10/12 14:18
 */
public class BusinessTypeData {
    //业务表格，第一行，每列名称
    private String name;
    //每列值的类型
    private String type;
    //存在的格式，布尔值、时间格式
    private String format;
    // 多值分隔符
    private String multiSeparator;
    // 多值场景
    private String multiScene;
    // 分解规则
    private Set<BusinessTypeRuleData> typeRuleData = new LinkedHashSet<>();
    //备注
    private String remark;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getMultiSeparator() {
        return multiSeparator;
    }

    public void setMultiSeparator(String multiSeparator) {
        this.multiSeparator = multiSeparator;
    }

    public String getMultiScene() {
        return multiScene;
    }

    public void setMultiScene(String multiScene) {
        this.multiScene = multiScene;
    }

    public Set<BusinessTypeRuleData> getTypeRuleData() {
        return typeRuleData;
    }

    public void setTypeRuleData(Set<BusinessTypeRuleData> typeRuleData) {
        this.typeRuleData = typeRuleData;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public boolean isMulti() {
        return Strings.isNotBlank(this.multiSeparator);
    }

    public boolean isSceneTypeMulti() {
        return ExcelMultiSceneTypeEnum.MULTI.name().equalsIgnoreCase(this.multiScene);
    }

    public boolean isSceneTypeSym() {
        return ExcelMultiSceneTypeEnum.SYM.name().equalsIgnoreCase(this.multiScene);
    }

    public boolean isColumnTypeString() {
        return ExcelColumnTypeEnum.STRING.name().equalsIgnoreCase(this.type);
    }

    public boolean isColumnTypeNumber() {
        return ExcelColumnTypeEnum.NUMBER.name().equalsIgnoreCase(this.type);
    }

    public boolean isColumnTypeBoolean() {
        return ExcelColumnTypeEnum.BOOLEAN.name().equalsIgnoreCase(this.type);
    }

    public boolean isColumnTypeDateTime() {
        return ExcelColumnTypeEnum.DATETIME.name().equalsIgnoreCase(this.type);
    }
}
