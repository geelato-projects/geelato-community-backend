package cn.geelato.web.platform.srvlog.spi;

import cn.geelato.web.platform.srvlog.model.SrvExceptionSummary;
import cn.geelato.web.platform.srvlog.model.SrvLogPage;
import cn.geelato.web.platform.srvlog.model.SrvLogRecord;

import java.util.List;

public interface SrvLogStore {
    void save(SrvLogRecord record);

    SrvLogPage listExceptions(String methodKey, SrvLogQueryOptions options);

    List<SrvExceptionSummary> listRecentExceptionSummary(int days, SrvSummaryQueryOptions options);
}

