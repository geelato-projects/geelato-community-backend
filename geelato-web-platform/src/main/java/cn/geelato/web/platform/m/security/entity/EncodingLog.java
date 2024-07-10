package cn.geelato.web.platform.m.security.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;

/**
 * @author diabl
 * @date 2023/8/3 9:55
 */
@Entity(name = "platform_encoding_log")
@Title(title = "编码记录")
public class EncodingLog extends BaseEntity {
    private String encodingId;
    private String template;// 模板
    private String example;
    private String exampleDate;// 时间
    private String exampleSerial;// 流水
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;

    @Title(title = "编码模板")
    @Col(name = "encoding_id")
    public String getEncodingId() {
        return encodingId;
    }

    public void setEncodingId(String encodingId) {
        this.encodingId = encodingId;
    }

    @Title(title = "实例")
    @Col(name = "example")
    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    @Title(title = "实例")
    @Col(name = "template")
    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }


    @Title(title = "实例")
    @Col(name = "example_serial")
    public String getExampleSerial() {
        return exampleSerial;
    }

    public void setExampleSerial(String exampleSerial) {
        this.exampleSerial = exampleSerial;
    }

    @Title(title = "实例")
    @Col(name = "example_date")
    public String getExampleDate() {
        return exampleDate;
    }

    public void setExampleDate(String exampleDate) {
        this.exampleDate = exampleDate;
    }

    @Title(title = "实例")
    @Col(name = "enable_status")
    public int getEnableStatus() {
        return enableStatus;
    }

    public void setEnableStatus(int enableStatus) {
        this.enableStatus = enableStatus;
    }
}
