package cn.geelato.core.orm.event.callback;

import cn.geelato.core.orm.event.DeleteEventContext;

/**
 * 函数式风格的删除前回调（C3）。用法与 {@link BeforeSaveCallback} 对称。
 */
@FunctionalInterface
public interface BeforeDeleteCallback {
    void beforeDelete(DeleteEventContext context);
}
