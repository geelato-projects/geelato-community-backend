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
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * @author diabl
 * @description: 编码
 */
@Getter
@Setter
@Entity(name = "platform_encoding")
@Title(title = "编码")
public class Encoding extends BaseEntity implements EntityEnableAble {
    @Title(title = "应用id")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "标题")
    private String title;
    @Title(title = "模板")
    private String template;
    @Title(title = "分隔符")
    private String separators;
    @Title(title = "示例")
    private String example;
    @Title(title = "状态", description = "0:停用|1:启用")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    @Title(title = "描述")
    private String description;
    // 解析
    @Transient
    private String formatExample;
    @Title(title = "日期格式")
    @Transient
    private String dateType;
    @Title(title = "位数")
    @Transient
    private int serialDigit;
    @Title(title = "顺序、随机")
    @Transient
    private String serialType;
    @Title(title = "顺序补位 0")
    @Transient
    private boolean coverPos = true;

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
