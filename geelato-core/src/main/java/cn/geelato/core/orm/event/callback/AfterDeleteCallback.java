package cn.geelato.core.orm.event.callback;

import cn.geelato.core.orm.event.DeleteEventContext;

/**
 * 函数式风格的删除后回调（C3）。用法与 {@link AfterSaveCallback} 对称。
 */
@FunctionalInterface
public interface AfterDeleteCallback {
    void afterDelete(DeleteEventContext context);
}
