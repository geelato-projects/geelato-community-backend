package cn.geelato.core.orm.event.callback;

import cn.geelato.core.orm.event.BeforeDeleteEventListener;
import cn.geelato.core.orm.event.BeforeSaveEventListener;
import cn.geelato.core.orm.event.DeleteEventContext;
import cn.geelato.core.orm.event.AfterDeleteEventListener;
import cn.geelato.core.orm.event.AfterSaveEventListener;
import cn.geelato.core.orm.event.SaveEventContext;

/**
 * Callback 到 Listener 的适配工具（C3）。
 *
 * <p>把函数式 callback 包装为对应的 {@code XxxEventListener}，供 Manager 的 {@code register*Callback} 使用。
 * 包装后的 listener：before/after 中只关心对应阶段的 callback，另一阶段留空；order 透传。
 */
public final class CallbackAdapters {

    private CallbackAdapters() {
    }

    /** 把 before-save callback 适配为 BeforeSaveEventListener。 */
    public static BeforeSaveEventListener forBeforeSave(BeforeSaveCallback callback, int order) {
        return new BeforeSaveEventListener() {
            @Override
            public void beforeSave(SaveEventContext context) {
                callback.beforeSave(context);
            }

            @Override
            public void afterSave(SaveEventContext context) {
                // 不关心
            }

            @Override
            public boolean enabled(SaveEventContext context) {
                return true;
            }

            @Override
            public boolean supports(SaveEventContext context) {
                return true;
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    /** 把 after-save callback 适配为 AfterSaveEventListener。 */
    public static AfterSaveEventListener forAfterSave(AfterSaveCallback callback, int order) {
        return new AfterSaveEventListener() {
            @Override
            public void beforeSave(SaveEventContext context) {
            }

            @Override
            public void afterSave(SaveEventContext context) {
                callback.afterSave(context);
            }

            @Override
            public boolean enabled(SaveEventContext context) {
                return true;
            }

            @Override
            public boolean supports(SaveEventContext context) {
                return true;
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    /** 把 before-delete callback 适配为 BeforeDeleteEventListener。 */
    public static BeforeDeleteEventListener forBeforeDelete(BeforeDeleteCallback callback, int order) {
        return new BeforeDeleteEventListener() {
            @Override
            public void beforeDelete(DeleteEventContext context) {
                callback.beforeDelete(context);
            }

            @Override
            public void afterDelete(DeleteEventContext context) {
            }

            @Override
            public boolean enabled(DeleteEventContext context) {
                return true;
            }

            @Override
            public boolean supports(DeleteEventContext context) {
                return true;
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    /** 把 after-delete callback 适配为 AfterDeleteEventListener。 */
    public static AfterDeleteEventListener forAfterDelete(AfterDeleteCallback callback, int order) {
        return new AfterDeleteEventListener() {
            @Override
            public void beforeDelete(DeleteEventContext context) {
            }

            @Override
            public void afterDelete(DeleteEventContext context) {
                callback.afterDelete(context);
            }

            @Override
            public boolean enabled(DeleteEventContext context) {
                return true;
            }

            @Override
            public boolean supports(DeleteEventContext context) {
                return true;
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }
}
