package cn.geelato.web.platform.srvlog.model;

import lombok.Data;

import java.util.List;

@Data
public class SrvLogPage {
    private long total;
    private int page;
    private int size;
    private List<SrvLogRecord> records;
}

