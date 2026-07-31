package cn.geelato.core.orm.event.callback;

import cn.geelato.core.orm.event.SaveEventContext;

/**
 * 函数式风格的保存前回调（C3）。
 *
 * <p>作为现有 {@code BeforeSaveEventListener} 空标记接口的<b>补充</b>，避免实现类被迫实现不关心的
 * {@code afterSave} 等空方法。可用 lambda 注册：
 * <pre>{@code
 * SaveEventManager.registerBeforeCallback(ctx -> { ... }, ctx -> true);
 * }</pre>
 *
 * <p>新老接口并存，不废弃旧接口；新代码推荐函数式接口。
 */
@FunctionalInterface
public interface BeforeSaveCallback {
    void beforeSave(SaveEventContext context);
}
