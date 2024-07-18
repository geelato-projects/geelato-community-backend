package cn.geelato.web.platform.m.security.entity;

import com.alibaba.fastjson2.JSON;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import cn.geelato.utils.UUIDUtils;
import cn.geelato.web.platform.enums.EncodingItemTypeEnum;
import cn.geelato.web.platform.enums.EncodingSerialTypeEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * @author diabl
 * @description: 编码
 * @date 2023/8/2 10:40
 */
@Entity(name = "platform_encoding")
@Title(title = "编码")
public class Encoding extends BaseEntity implements EntityEnableAble {
    private String title;
    private String template;
    private String separators;
    private String example;
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    private String description;
    private String appId;
    // 解析
    private String formatExample;
    private String dateType;// 日期格式
    private int serialDigit;// 位数
    private String serialType;// 顺序、随机
    private boolean coverPos = true;// 顺序补位 0

    @Title(title = "标题")
    @Col(name = "title")
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Title(title = "模板")
    @Col(name = "template")
    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    @Title(title = "分隔符")
    @Col(name = "separators")
    public String getSeparators() {
        return separators;
    }

    public void setSeparators(String separators) {
        this.separators = separators;
    }

    @Title(title = "示例")
    @Col(name = "example")
    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    @Title(title = "描述")
    @Col(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Title(title = "应用id")
    @Col(name = "app_id")
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
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

    @Transient
    public String getFormatExample() {
        return formatExample;
    }

    public void setFormatExample(String formatExample) {
        this.formatExample = formatExample;
    }

    @Transient
    public String getDateType() {
        return dateType;
    }

    public void setDateType(String dateType) {
        this.dateType = dateType;
    }

    @Transient
    public int getSerialDigit() {
        return serialDigit;
    }

    public void setSerialDigit(int serialDigit) {
        this.serialDigit = serialDigit;
    }

    @Transient
    public String getSerialType() {
        return serialType;
    }

    public void setSerialType(String serialType) {
        this.serialType = serialType;
    }

    @Transient
    public boolean isCoverPos() {
        return coverPos;
    }

    public void setCoverPos(boolean coverPos) {
        this.coverPos = coverPos;
    }

    @Override
    public void afterSet() {
        if (Strings.isNotBlank(getTemplate())) {
            List<EncodingItem> itemList = JSON.parseArray(getTemplate(), EncodingItem.class);
            if (itemList != null && !itemList.isEmpty()) {
                List<String> examples = new ArrayList<>();
                for (EncodingItem item : itemList) {
                    if (EncodingItemTypeEnum.CONSTANT.getValue().equals(item.getItemType())) {
                        // 常量
                        if (Strings.isNotBlank(item.getConstantValue())) {
                            examples.add(item.getConstantValue());
                        }
                    } else if (EncodingItemTypeEnum.VARIABLE.getValue().equals(item.getItemType())) {
                        // 常量
                        if (Strings.isNotBlank(item.getConstantValue())) {
                            examples.add(String.format("{%s}", item.getConstantValue()));
                        }
                    } else if (EncodingItemTypeEnum.ARGUMENT.getValue().equals(item.getItemType())) {
                        // 常量
                        if (Strings.isNotBlank(item.getConstantValue())) {
                            examples.add(String.format("[%s]", item.getConstantValue()));
                        }
                    } else if (EncodingItemTypeEnum.SERIAL.getValue().equals(item.getItemType())) {
                        // 序列号
                        if (EncodingSerialTypeEnum.ORDER.getValue().equals(item.getSerialType())) {
                            if (item.isCoverPos()) {
                                examples.add(String.format("%0" + item.getSerialDigit() + "d", 1));
                            } else {
                                examples.add("1");
                            }
                        } else if (EncodingSerialTypeEnum.RANDOM.getValue().equals(item.getSerialType())) {
                            examples.add(UUIDUtils.generateFixation(item.getSerialDigit(), 8));
                        }
                    } else if (EncodingItemTypeEnum.DATE.getValue().equals(item.getItemType())) {
                        // 日期
                        if (Strings.isNotBlank(item.getDateType())) {
                            examples.add(item.getDateType());
                            setDateType(item.getDateType());
                        }
                    }
                }
                String separator = Strings.isNotBlank(getSeparators()) ? getSeparators() : "";
                setFormatExample(String.join(separator, examples));
            }
        }
    }
}
