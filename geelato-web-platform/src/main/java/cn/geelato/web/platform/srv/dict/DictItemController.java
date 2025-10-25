package cn.geelato.web.platform.srv.dict;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.dict.mapper.DictItemMapper;
import cn.geelato.web.platform.srv.dict.vo.DictItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字典项控制器
 * 提供字典项相关的REST API接口
 * 
 * @author system
 */
@ApiRestController(value = "/dictItem")
@Slf4j
public class DictItemController extends BaseController {
    
    private final DictItemMapper dictItemMapper;

    @Autowired
    public DictItemController(DictItemMapper dictItemMapper) {
        this.dictItemMapper = dictItemMapper;
    }

    /**
     * 根据字典编码获取字典项列表
     * 
     * @param code 字典编码
     * @return 字典项列表
     */
    @RequestMapping(value = "/{code}", method = RequestMethod.GET)
    public ApiResult<List<DictItemVO>> getDictItemsByCode(@PathVariable String code) {
        try {
            List<Map<String, Object>> dictItemMaps = dictItemMapper.selectByDictCode(code);
            List<DictItemVO> dictItems = dictItemMaps.stream()
                    .map(map -> new DictItemVO(
                            (String) map.get("itemCode"),
                            (String) map.get("itemName")
                    ))
                    .collect(Collectors.toList());
            
            return ApiResult.success(dictItems);
        } catch (Exception e) {
            log.error("获取字典项失败，字典编码: {}, 错误信息: {}", code, e.getMessage(), e);
            return ApiResult.fail("获取字典项失败: " + e.getMessage());
        }
    }
}