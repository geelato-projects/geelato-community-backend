package cn.geelato.orm.query;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.orm.Filter;
import cn.geelato.orm.executor.MetaCommandExecutor;
import cn.geelato.orm.runtime.OrmRuntimeProvider;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public abstract class MetaOperate<T extends MetaOperate<T>> {
    protected String entityName;
    protected Class<?> entityClass;
    @Getter
    protected final List<Filter> filters = new ArrayList<>();
    @Getter
    protected final Map<String, Object> viewTemplateParams = new LinkedHashMap<>();
    @Getter
    protected String connectId;
    protected boolean withMeta;

    protected T self() {
        return (T) this;
    }

    public String resolveEntityName() {
        if (entityName != null && !entityName.isBlank()) {
            return entityName;
        }
        return entityClass != null ? MetaManager.singleInstance().get(entityClass).getEntityName() : null;
    }

    public T useDataSource(String connectId) {
        this.connectId = connectId;
        return self();
    }

    public T viewParams(Map<String, Object> params) {
        if (params != null) {
            this.viewTemplateParams.putAll(params);
        }
        return self();
    }

    public T withMeta(boolean withMeta) {
        this.withMeta = withMeta;
        return self();
    }

    protected MetaCommandExecutor executor() {
        return BeansUtils.getBean(OrmRuntimeProvider.class).metaCommandExecutor();
    }

}
