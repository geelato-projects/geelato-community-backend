package cn.geelato.core.meta.model.field;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author geemeta
 * @description 用于对外发布元数据服务信息，去掉数据库的部分元数据信息，如表名等，详细的字段元数据参见@see FieldMeta
 */
@Getter
@Setter
public class SimpleFieldMeta implements Serializable {
    private String name;
    private String type;
    private String title;
    private String comment;
    private boolean nullable;
    private long charMaxLength;
    private int precision;
    private int scale;
    private String selectType;
    private String typeExtra;
    private String extraValue;
    private String extraMap;
    private String defaultValue;
}
