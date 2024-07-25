package cn.geelato.core.constants;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.enums.TableForeignAction;

/**
 * @author diabl
 */
public class ColumnDefault {
    /**
     * 默认排序 默认值[999]
     */
    public static final long SEQ_NO_VALUE = 999;
    public static final long SEQ_NO_ZEROTH = 0;
    public static final long SEQ_NO_FIRST = 1;
    public static final long SEQ_NO_DELETE = 999999999L;
    /**
     * 是否删除 字段名称
     */
    public static final String DEL_STATUS_FIELD = "delStatus";
    /**
     * 是否删除 默认值 - 未删除[0]
     */
    public static final int DEL_STATUS_VALUE = DeleteStatusEnum.NO.getCode();
    /**
     * 启用状态 字段名称
     */
    public static final String ENABLE_STATUS_FIELD = "enableStatus";
    /**
     * 启用状态 默认值 - 启用[1]
     */
    public static final int ENABLE_STATUS_VALUE = EnableStatusEnum.ENABLED.getCode();
    /**
     * 外键操作类型 默认值 - [NO ACTION]
     */
    public static final String FOREIGN_ACTION_VALUE = TableForeignAction.NO_ACTION.getCode();

}
