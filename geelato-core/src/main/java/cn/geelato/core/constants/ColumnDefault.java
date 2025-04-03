package cn.geelato.core.constants;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.enums.TableForeignAction;

/**
 * @author diabl
 */
public class ColumnDefault {
    /**
     * 默认排序 默认值[0]
     */
    public static final long SEQ_NO_VALUE = 0;
    public static final long SEQ_NO_FIRST = 1;
    public static final long SEQ_NO_DELETE = 999999999L;
    /**
     * 是否删除,删除时间 字段名称
     */
    public static final String DEL_STATUS_FIELD = "delStatus";
    public static final String DEL_STATUS_COLUMN = "del_status";
    public static final String DELETE_AT_FIELD = "deleteAt";
    public static final String DELETE_AT_COLUMN = "delete_at";
    /**
     * 是否删除 默认值 - 未删除[0]
     */
    public static final int DEL_STATUS_VALUE = DeleteStatusEnum.NO.getValue();
    /**
     * 启用状态 字段名称
     */
    public static final String ENABLE_STATUS_FIELD = "enableStatus";
    public static final String ENABLE_STATUS_COLUMN = "enable_status";
    /**
     * 启用状态 默认值 - 启用[1]
     */
    public static final int ENABLE_STATUS_VALUE = EnableStatusEnum.ENABLED.getValue();
    /**
     * 外键操作类型 默认值 - [NO ACTION]
     */
    public static final String FOREIGN_ACTION_VALUE = TableForeignAction.NO_ACTION.getValue();
    /**
     * 应用id，租户编码 字段名称
     */
    public static final String APP_ID_FIELD = "appId";
    public static final String APP_ID_COLUMN = "app_id";
    public static final String TENANT_CODE_FIELD = "tenantCode";
    public static final String TENANT_CODE_COLUMN = "tenant_code";
}
