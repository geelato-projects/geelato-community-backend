package cn.geelato.web.platform.srvlog.service;

import cn.geelato.web.platform.srvlog.boot.SrvLogProperties;
import cn.geelato.web.platform.srvlog.model.SrvExceptionSummary;
import cn.geelato.web.platform.srvlog.model.SrvLogPage;
import cn.geelato.web.platform.srvlog.model.SrvLogRecord;
import cn.geelato.web.platform.srvlog.registry.ApiRestControllerSrvLogRegistry;
import cn.geelato.web.platform.srvlog.spi.SrvLogQueryOptions;
import cn.geelato.web.platform.srvlog.spi.SrvLogStore;
import cn.geelato.web.platform.srvlog.spi.SrvSummaryQueryOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SrvLogService {
    private final SrvLogProperties properties;
    private final ObjectMapper objectMapper;
    private final SrvLogStore store;
    private final ObjectProvider<ApiRestControllerSrvLogRegistry> registryProvider;

    public SrvLogService(SrvLogProperties properties, ObjectMapper objectMapper, SrvLogStore store, ObjectProvider<ApiRestControllerSrvLogRegistry> registryProvider) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.store = store;
        this.registryProvider = registryProvider;
    }

    public void save(SrvLogRecord record) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return;
        }
        if (!Boolean.TRUE.equals(properties.getRecordSuccess()) && record.isSuccess()) {
            return;
        }
        if (record.getId() == null || record.getId().isBlank()) {
            record.setId(UUID.randomUUID().toString().replace("-", ""));
        }
        int maxFieldLength = properties.getMaxFieldLength() == null ? -1 : properties.getMaxFieldLength();
        if (maxFieldLength > 0) {
            record.setArgsJson(truncate(record.getArgsJson(), maxFieldLength));
            record.setResultJson(truncate(record.getResultJson(), maxFieldLength));
            record.setStackTrace(truncate(record.getStackTrace(), maxFieldLength));
            record.setExceptionMessage(truncate(record.getExceptionMessage(), maxFieldLength));
        }
        store.save(record);
    }

    public SrvLogPage listExceptions(String methodKey, SrvLogQueryOptions options) {
        SrvLogPage p = store.listExceptions(methodKey, options);
        ApiRestControllerSrvLogRegistry registry = registryProvider.getIfAvailable();
        if (p != null && p.getRecords() != null) {
            for (SrvLogRecord r : p.getRecords()) {
                if (r.getHandlerSignature() == null || r.getHandlerSignature().isBlank()) {
                    if (registry != null) {
                        r.setHandlerSignature(registry.resolveHandlerSignature(r.getMethodKey()));
                    }
                }
            }
        }
        return p;
    }

    public List<SrvExceptionSummary> listRecentExceptionSummary(int days, SrvSummaryQueryOptions options) {
        List<SrvExceptionSummary> list = store.listRecentExceptionSummary(days, options);
        ApiRestControllerSrvLogRegistry registry = registryProvider.getIfAvailable();
        for (SrvExceptionSummary s : list) {
            if (s.getHandlerSignature() == null || s.getHandlerSignature().isBlank()) {
                if (registry != null) {
                    s.setHandlerSignature(registry.resolveHandlerSignature(s.getMethodKey()));
                }
            }
        }
        return list;
    }

    public String toJson(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception e) {
            return String.valueOf(v);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null || maxLen <= 0) {
            return s;
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}

