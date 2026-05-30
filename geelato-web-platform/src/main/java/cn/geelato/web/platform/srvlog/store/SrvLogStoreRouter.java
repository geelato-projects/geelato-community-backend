package cn.geelato.web.platform.srvlog.store;

import cn.geelato.web.platform.srvlog.boot.SrvLogProperties;
import cn.geelato.web.platform.srvlog.model.SrvExceptionSummary;
import cn.geelato.web.platform.srvlog.model.SrvLogPage;
import cn.geelato.web.platform.srvlog.model.SrvLogRecord;
import cn.geelato.web.platform.srvlog.spi.SrvLogQueryOptions;
import cn.geelato.web.platform.srvlog.spi.SrvLogStore;
import cn.geelato.web.platform.srvlog.spi.SrvSummaryQueryOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Primary
@Component
public class SrvLogStoreRouter implements SrvLogStore {
    private final SrvLogProperties properties;
    private final ObjectProvider<EsSrvLogStore> esStoreProvider;
    private final ObjectProvider<FileSrvLogStore> fileStoreProvider;

    public SrvLogStoreRouter(SrvLogProperties properties, ObjectProvider<EsSrvLogStore> esStoreProvider, ObjectProvider<FileSrvLogStore> fileStoreProvider) {
        this.properties = properties;
        this.esStoreProvider = esStoreProvider;
        this.fileStoreProvider = fileStoreProvider;
    }

    @Override
    public void save(SrvLogRecord record) {
        SrvLogStore s = resolveStore();
        if (s != null) {
            s.save(record);
        }
    }

    @Override
    public SrvLogPage listExceptions(String methodKey, SrvLogQueryOptions options) {
        SrvLogStore s = resolveStore();
        if (s == null) {
            SrvLogPage p = new SrvLogPage();
            p.setTotal(0);
            p.setPage(options.getPage());
            p.setSize(options.getSize());
            p.setRecords(Collections.emptyList());
            return p;
        }
        return s.listExceptions(methodKey, options);
    }

    @Override
    public List<SrvExceptionSummary> listRecentExceptionSummary(int days, SrvSummaryQueryOptions options) {
        SrvLogStore s = resolveStore();
        if (s == null) {
            return Collections.emptyList();
        }
        return s.listRecentExceptionSummary(days, options);
    }

    private SrvLogStore resolveStore() {
        String type = properties.getStoreType();
        if (type == null) {
            type = "es";
        }
        type = type.trim().toLowerCase();
        if ("file".equals(type)) {
            return fileStoreProvider.getIfAvailable();
        }
        return esStoreProvider.getIfAvailable();
    }
}

