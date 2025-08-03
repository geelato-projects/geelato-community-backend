package cn.geelato.web.platform.m.security.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Getter
@Setter
@Entity(name = "platform_encoding_log")
@Title(title = "编码记录")
public class EncodingLog extends BaseEntity {
    @Title(title = "编码模板")
    @Col(name = "encoding_id")
    private String encodingId;
    @Title(title = "模板")
    private String template;
    @Title(title = "实例")
    private String example;
    @Title(title = "时间")
    @Col(name = "example_date")
    private String exampleDate;
    @Title(title = "流水")
    @Col(name = "example_serial")
    private String exampleSerial;
    @Title(title = "状态")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    @Title(title = "备注")
    private String description;
}
