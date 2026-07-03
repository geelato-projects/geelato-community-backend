package cn.geelato.web.platform.srvlog.spi;

import lombok.Data;

@Data
public class SrvLogQueryOptions {
    private Long startTime;
    private Long endTime;
    private int page = 1;
    private int size = 20;
}

