package cn.geelato.web.platform.mapper;

import cn.geelato.meta.DictItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 字典项Mapper接口
 * 
 * @author system
 */
@Mapper
public interface DictItemMapper extends BaseMapper<DictItem> {
    
    /**
     * 根据字典编码查询字典项
     * 
     * @param dictCode 字典编码
     * @return 字典项列表
     */
    List<Map<String, Object>> selectByDictCode(@Param("dictCode") String dictCode);
}