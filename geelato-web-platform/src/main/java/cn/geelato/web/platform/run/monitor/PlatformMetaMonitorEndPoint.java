package cn.geelato.web.platform.run.monitor;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.lang.monitor.HealthEndpoint;
import cn.geelato.lang.monitor.MetaMonitorEndPoint;
import com.alibaba.fastjson2.JSON;
import com.itextpdf.text.Meta;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
public class PlatformMetaMonitorEndPoint implements MetaMonitorEndPoint {
    MetaManager metaManager= MetaManager.singleInstance();
    @Override
    public Map<String, Map<String, String>> scanMeta() {
        Collection<EntityMeta> allEntityMeta= metaManager.getAll();
        Map<String,Map<String,String>> map=new HashMap<>();
        for(EntityMeta entityMeta:allEntityMeta){
            Map<String, String> metaMap=new HashMap<>();
            entityMeta.getFieldMetas().forEach(fieldMeta -> {
                metaMap.put(fieldMeta.getFieldName(),
                        JSON.toJSONString(fieldMeta.getColumnMeta()));
            });
            map.put(entityMeta.getEntityName(),metaMap);
        }
        return map;
    }
}
