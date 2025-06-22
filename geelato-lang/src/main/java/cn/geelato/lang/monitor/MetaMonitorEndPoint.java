package cn.geelato.lang.monitor;

import java.util.Map;

public interface MetaMonitorEndPoint {

    Map<String,Map<String,String>> scanMeta();
}
