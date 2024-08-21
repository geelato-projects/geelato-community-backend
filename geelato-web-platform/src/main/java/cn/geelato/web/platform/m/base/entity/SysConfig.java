package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import cn.geelato.web.platform.enums.SysConfigValueTypeEnum;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

/**
 * @author diabl
 * @Description 应用参数配置，
 */
@Getter
@Setter
@Entity(name = "platform_sys_config")
@Title(title = "配置")
public class SysConfig extends BaseSortableEntity implements EntityEnableAble {

    @Col(name = "app_id")
    @Title(title = "应用ID")
    private String appId;


    @Col(name = "key_type")
    @Title(title = "键类型")
    private String keyType;

    @Col(name = "config_key")
    @Title(title = "配置键")
    private String configKey;
    @Col(name = "value_type")
    @Title(title = "值类型")
    private String valueType;

    @Col(name = "config_value")
    @Title(title = "配置值")
    private String configValue;

    @Transient
    private String configAssist;

    @Col(name = "enable_status")
    @Title(title = "启用状态")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    private String remark;
    private String purpose;

    @Col(name = "encrypted")
    @Title(title = "是否加密")
    private boolean encrypted = false;

    @Col(name = "sm2_key")
    @Title(title = "公私钥")
    private String sm2Key;


    @Override
    public void afterSet() {
        this.setEncrypted(SysConfigValueTypeEnum.ENCRYPT.getValue().equalsIgnoreCase(this.getValueType()));
        if (Strings.isBlank(this.getAppId())) {
            this.setAppId(null);
        }
    }
}
