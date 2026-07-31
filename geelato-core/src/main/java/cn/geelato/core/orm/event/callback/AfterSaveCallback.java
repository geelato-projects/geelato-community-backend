package cn.geelato.core.orm.event.callback;

import cn.geelato.core.orm.event.SaveEventContext;

/**
 * 函数式风格的保存后回调（C3）。
 *
 * <p>注意：after 回调仍走异步线程池（与 {@code AfterSaveEventListener.afterSave} 一致），
 * 事务可见性不确定；需事务感知请用 {@code TransactionalAfterSaveEventListener}。
 *
 * @see BeforeSaveCallback
 */
@FunctionalInterface
public interface AfterSaveCallback {
    void afterSave(SaveEventContext context);
}
