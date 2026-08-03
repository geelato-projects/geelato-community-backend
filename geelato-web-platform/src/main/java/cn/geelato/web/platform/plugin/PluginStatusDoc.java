package cn.geelato.web.platform.plugin;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 插件开关 JSON 文档模型，平台级与租户级共用。
 * <p>平台级示例 {@code plugins-enabled.json}：
 * <pre>{ "enabled": ["ocr-plugin"], "disabled": [], "updatedAt": "...", "updatedBy": "admin" }</pre>
 * 租户级示例 {@code tenants/tenant_geelato.json}：
 * <pre>{ "tenantCode": "geelato", "enabled": ["ocr-plugin"], "updatedAt": "...", "updatedBy": "admin" }</pre>
 *
 * @author geelato
 */
@Data
public class PluginStatusDoc {

    /** 租户编码（平台级文档可为空）。 */
    private String tenantCode;
    /** 已启用的插件 id 列表。 */
    private List<String> enabled = new ArrayList<>();
    /** 已禁用的插件 id 列表（仅平台级语义使用，租户级默认未启用即视为禁用）。 */
    private List<String> disabled = new ArrayList<>();
    /** 最后更新时间（排查用，非审计）。 */
    private String updatedAt;
    /** 最后更新人（排查用，非审计）。 */
    private String updatedBy;
}
