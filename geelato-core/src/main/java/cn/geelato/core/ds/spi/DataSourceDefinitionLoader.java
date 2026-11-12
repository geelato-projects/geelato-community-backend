package cn.geelato.core.ds.spi;

import cn.geelato.core.orm.Dao;

import java.util.List;
import java.util.Map;

/**
 * 动态数据源定义加载器。
 */
public interface DataSourceDefinitionLoader {

    List<Map<String, Object>> load(Dao dao);

    /**
     * 运行期按 connectId 实时补载单条连接定义（快照 miss 时的自愈来源），未实现或不存在返回 null。
     */
    default Map<String, Object> loadOne(String key) {
        return null;
    }
}
