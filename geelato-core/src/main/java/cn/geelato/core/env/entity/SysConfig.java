package cn.geelato.core.env.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Getter
@Setter
public class SysConfig {

    private String configKey;
    private String configValue;
    private String purpose;
    private String appId;
    private String tenantCode;
}
