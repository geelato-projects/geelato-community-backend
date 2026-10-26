package cn.geelato.script.kit;

import cn.geelato.script.core.IdentitySnapshot;
import cn.geelato.script.core.TraceEntry;
import cn.geelato.script.kit.sql.ConnectPoolProvider;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单次执行的 kit 上下文：scoped kit 经构造器或工厂持有它，共享行数计数、配置与执行级属性袋
 * （消费方把规则元信息等放进 attributes，kit 按约定读取）。
 */
@Slf4j
@Getter
public class KitContext {

    private static final int MAX_TRACE = 100;

    private final AtomicLong rowCount = new AtomicLong();

    private final DataSourceSupplier dataSourceSupplier;
    private final int sqlRowLimit;
    private final int sqlQueryTimeoutMs;

    private final boolean httpEnabled;
    private final List<String> httpAllowlist;
    private final int httpTimeoutMs;

    private final Map<String, Object> attributes;

    private final String execId;
    private final IdentitySnapshot identity;
    private final DaoSupplier daoSupplier;
    private final OrmExecutionWrapper ormWrapper;
    private final String aesKey;
    private final boolean daoWriteEnabled;
    private final boolean daoDryRun;
    /** 联邦查询按 connectId 取池的提供器（null 走 core DataSourceManager 缺省）。 */
    private final ConnectPoolProvider connectPoolProvider;
    /** 本次执行的注入变量（$ctx/$params 等，engine 侧 effectiveBindings 引用）——$gl.ctx/$gl.parameter 数据源。 */
    private final Map<String, Object> bindings;
    private final List<TraceEntry> trace = new ArrayList<>();

    public KitContext(DataSourceSupplier dataSourceSupplier,
                         int sqlRowLimit, int sqlQueryTimeoutMs,
                         boolean httpEnabled, List<String> httpAllowlist, int httpTimeoutMs) {
        this(dataSourceSupplier, sqlRowLimit, sqlQueryTimeoutMs, httpEnabled, httpAllowlist, httpTimeoutMs, null);
    }

    public KitContext(DataSourceSupplier dataSourceSupplier,
                         int sqlRowLimit, int sqlQueryTimeoutMs,
                         boolean httpEnabled, List<String> httpAllowlist, int httpTimeoutMs,
                         Map<String, Object> attributes) {
        this(dataSourceSupplier, sqlRowLimit, sqlQueryTimeoutMs, httpEnabled, httpAllowlist, httpTimeoutMs,
                attributes, null, null, null, null);
    }

    public KitContext(DataSourceSupplier dataSourceSupplier,
                         int sqlRowLimit, int sqlQueryTimeoutMs,
                         boolean httpEnabled, List<String> httpAllowlist, int httpTimeoutMs,
                         Map<String, Object> attributes,
                         String execId, IdentitySnapshot identity,
                         DaoSupplier daoSupplier, OrmExecutionWrapper ormWrapper) {
        this(dataSourceSupplier, sqlRowLimit, sqlQueryTimeoutMs, httpEnabled, httpAllowlist, httpTimeoutMs,
                attributes, execId, identity, daoSupplier, ormWrapper, null);
    }

    public KitContext(DataSourceSupplier dataSourceSupplier,
                         int sqlRowLimit, int sqlQueryTimeoutMs,
                         boolean httpEnabled, List<String> httpAllowlist, int httpTimeoutMs,
                         Map<String, Object> attributes,
                         String execId, IdentitySnapshot identity,
                         DaoSupplier daoSupplier, OrmExecutionWrapper ormWrapper, String aesKey) {
        this(dataSourceSupplier, sqlRowLimit, sqlQueryTimeoutMs, httpEnabled, httpAllowlist, httpTimeoutMs,
                attributes, execId, identity, daoSupplier, ormWrapper, aesKey, false, true, null, null);
    }

    public KitContext(DataSourceSupplier dataSourceSupplier,
                         int sqlRowLimit, int sqlQueryTimeoutMs,
                         boolean httpEnabled, List<String> httpAllowlist, int httpTimeoutMs,
                         Map<String, Object> attributes,
                         String execId, IdentitySnapshot identity,
                         DaoSupplier daoSupplier, OrmExecutionWrapper ormWrapper, String aesKey,
                         boolean daoWriteEnabled, boolean daoDryRun, Map<String, Object> bindings) {
        this(dataSourceSupplier, sqlRowLimit, sqlQueryTimeoutMs, httpEnabled, httpAllowlist, httpTimeoutMs,
                attributes, execId, identity, daoSupplier, ormWrapper, aesKey, daoWriteEnabled, daoDryRun, bindings, null);
    }

    public KitContext(DataSourceSupplier dataSourceSupplier,
                         int sqlRowLimit, int sqlQueryTimeoutMs,
                         boolean httpEnabled, List<String> httpAllowlist, int httpTimeoutMs,
                         Map<String, Object> attributes,
                         String execId, IdentitySnapshot identity,
                         DaoSupplier daoSupplier, OrmExecutionWrapper ormWrapper, String aesKey,
                         boolean daoWriteEnabled, boolean daoDryRun, Map<String, Object> bindings,
                         ConnectPoolProvider connectPoolProvider) {
        this.dataSourceSupplier = dataSourceSupplier;
        this.sqlRowLimit = sqlRowLimit;
        this.sqlQueryTimeoutMs = sqlQueryTimeoutMs;
        this.httpEnabled = httpEnabled;
        this.httpAllowlist = httpAllowlist == null ? List.of() : httpAllowlist;
        this.httpTimeoutMs = httpTimeoutMs;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        this.execId = execId;
        this.identity = identity == null ? IdentitySnapshot.empty() : identity;
        this.daoSupplier = daoSupplier;
        this.ormWrapper = ormWrapper == null ? OrmExecutionWrapper.DIRECT : ormWrapper;
        this.aesKey = aesKey;
        this.daoWriteEnabled = daoWriteEnabled;
        this.daoDryRun = daoDryRun;
        this.bindings = bindings == null ? Map.of() : bindings;
        this.connectPoolProvider = connectPoolProvider;
    }

    public static Builder builder() {
        return new Builder();
    }

    public long addRows(long rows) {
        return rowCount.addAndGet(rows);
    }

    public synchronized void record(TraceEntry entry) {
        if (entry == null) {
            return;
        }
        if (trace.size() < MAX_TRACE) {
            trace.add(entry);
        }
        log.info("KIT exec={} {}.{} rows={} cost={}ms :: {}", execId, entry.getNs(), entry.getMethod(),
                entry.getRows(), entry.getCostMs(), entry.getSummary());
    }

    public synchronized List<TraceEntry> getTraceSnapshot() {
        return List.copyOf(trace);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttr(String key) {
        return (T) attributes.get(key);
    }

    public static class Builder {

        private DataSourceSupplier dataSourceSupplier;
        private int sqlRowLimit = 10_000;
        private int sqlQueryTimeoutMs = 10_000;
        private boolean httpEnabled;
        private List<String> httpAllowlist = List.of();
        private int httpTimeoutMs = 5_000;
        private Map<String, Object> attributes;
        private String execId;
        private IdentitySnapshot identity;
        private DaoSupplier daoSupplier;
        private OrmExecutionWrapper ormWrapper;
        private String aesKey;
        private boolean daoWriteEnabled;
        private boolean daoDryRun = true;
        private Map<String, Object> bindings;
        private ConnectPoolProvider connectPoolProvider;

        public Builder dataSourceSupplier(DataSourceSupplier v) {
            this.dataSourceSupplier = v;
            return this;
        }

        public Builder sqlRowLimit(int v) {
            this.sqlRowLimit = v;
            return this;
        }

        public Builder sqlQueryTimeoutMs(int v) {
            this.sqlQueryTimeoutMs = v;
            return this;
        }

        public Builder httpEnabled(boolean v) {
            this.httpEnabled = v;
            return this;
        }

        public Builder httpAllowlist(List<String> v) {
            this.httpAllowlist = v;
            return this;
        }

        public Builder httpTimeoutMs(int v) {
            this.httpTimeoutMs = v;
            return this;
        }

        public Builder attributes(Map<String, Object> v) {
            this.attributes = v;
            return this;
        }

        public Builder execId(String v) {
            this.execId = v;
            return this;
        }

        public Builder identity(IdentitySnapshot v) {
            this.identity = v;
            return this;
        }

        public Builder daoSupplier(DaoSupplier v) {
            this.daoSupplier = v;
            return this;
        }

        public Builder ormWrapper(OrmExecutionWrapper v) {
            this.ormWrapper = v;
            return this;
        }

        public Builder aesKey(String v) {
            this.aesKey = v;
            return this;
        }

        public Builder daoWriteEnabled(boolean v) {
            this.daoWriteEnabled = v;
            return this;
        }

        public Builder daoDryRun(boolean v) {
            this.daoDryRun = v;
            return this;
        }

        public Builder bindings(Map<String, Object> v) {
            this.bindings = v;
            return this;
        }

        public Builder connectPoolProvider(ConnectPoolProvider v) {
            this.connectPoolProvider = v;
            return this;
        }

        public KitContext build() {
            return new KitContext(dataSourceSupplier, sqlRowLimit, sqlQueryTimeoutMs,
                    httpEnabled, httpAllowlist, httpTimeoutMs, attributes,
                    execId, identity, daoSupplier, ormWrapper, aesKey,
                    daoWriteEnabled, daoDryRun, bindings, connectPoolProvider);
        }
    }
}
