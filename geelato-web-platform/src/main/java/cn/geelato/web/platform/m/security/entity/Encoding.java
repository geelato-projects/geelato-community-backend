package cn.geelato.web.platform.m.security.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import cn.geelato.utils.UUIDUtils;
import cn.geelato.web.platform.m.security.enums.EncodingItemTypeEnum;
import cn.geelato.web.platform.m.security.enums.EncodingSerialTypeEnum;
import com.alibaba.fastjson2.JSON;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * @author diabl
 * @description: 编码
 */
@Setter
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

    @Title(title = "模板")
    @Col(name = "template")
    public String getTemplate() {
        return template;
    }

    @Title(title = "分隔符")
    @Col(name = "separators")
    public String getSeparators() {
        return separators;
    }

    @Title(title = "示例")
    @Col(name = "example")
    public String getExample() {
        return example;
    }

    @Title(title = "描述")
    @Col(name = "description")
    public String getDescription() {
        return description;
    }

    @Title(title = "应用id")
    @Col(name = "app_id")
    public String getAppId() {
        return appId;
    }

    @Override
    @Title(title = "状态", description = "0:停用|1:启用")
    @Col(name = "enable_status")
    public int getEnableStatus() {
        return enableStatus;
    }

    @Transient
    public String getFormatExample() {
        return formatExample;
    }

    @Transient
    public String getDateType() {
        return dateType;
    }

    @Transient
    public int getSerialDigit() {
        return serialDigit;
    }

    @Transient
    public String getSerialType() {
        return serialType;
    }

    @Transient
    public boolean isCoverPos() {
        return coverPos;
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
