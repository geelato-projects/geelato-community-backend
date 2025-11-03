package cn.geelato.web.platform.srv.base;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.mapper.DictItemMapper;
import cn.geelato.web.platform.utils.CacheUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@ApiRestController("/dictionary")
public class DictionaryController extends BaseController {

        private final DictItemMapper dictItemMapper;
    
    /**
     * 全局刷新缓存标志，当为true时，强制从数据库获取并更新缓存
     */
    private static boolean forceRefreshCache = false;

    @Autowired
    public DictionaryController(DictItemMapper dictItemMapper) {
        this.dictItemMapper = dictItemMapper;
    }

    /**
     * 根据字典编码获取字典项列表
     * 优先从缓存获取，如果缓存不存在或强制刷新标志为true，则从数据库获取并更新缓存
     * 
     * @param code 字典编码
     * @return 字典项列表
     */
    @RequestMapping(value = "/{code}", method = RequestMethod.GET)
    public ApiResult<List<DictionaryItem>> getDictItemsByCode(@PathVariable String code) {
        try {
            String cacheKey = generateDictCacheKey(code);
            
            // 检查是否需要从缓存获取
            if (!forceRefreshCache && CacheUtil.exists(cacheKey)) {
                log.debug("从缓存获取字典项，字典编码: {}", code);
                @SuppressWarnings("unchecked")
                List<DictionaryItem> cachedItems = (List<DictionaryItem>) CacheUtil.get(cacheKey);
                return ApiResult.success(cachedItems);
            }
            
            // 从数据库获取
            log.debug("从数据库获取字典项，字典编码: {}", code);
            List<Map<String, Object>> dictItemMaps = dictItemMapper.selectByDictCode(code);
            List<DictionaryItem> dictItems = dictItemMaps.stream()
                    .map(map -> new DictionaryItem(
                            (String) map.get("itemCode"),
                            (String) map.get("itemName")
                    ))
                    .collect(Collectors.toList());
            
            // 更新缓存
            CacheUtil.put(cacheKey, dictItems);
            
            // 如果是强制刷新，完成后重置标志
            if (forceRefreshCache) {
                forceRefreshCache = false;
            }
            
            return ApiResult.success(dictItems);
        } catch (Exception e) {
            log.error("获取字典项失败，字典编码: {}, 错误信息: {}", code, e.getMessage(), e);
            return ApiResult.fail("获取字典项失败: " + e.getMessage());
        }
    }
    
    /**
     * 设置强制刷新缓存标志
     * 
     * @param refresh 是否强制刷新
     * @return 操作结果
     */
    @RequestMapping(value = "/cache/refresh", method = RequestMethod.POST)
    public ApiResult<String> setForceRefreshCache(@RequestParam(defaultValue = "true") boolean refresh) {
        forceRefreshCache = refresh;
        log.info("设置字典缓存强制刷新标志为: {}", refresh);
        return ApiResult.success("设置成功");
    }
    
    /**
     * 清除指定字典的缓存
     * 
     * @param code 字典编码，如果为null则清除所有字典缓存
     * @return 操作结果
     */
    @RequestMapping(value = "/cache/clear", method = RequestMethod.POST)
    public ApiResult<String> clearDictionaryCache(@RequestParam(required = false) String code) {
        try {
            if (code != null) {
                String cacheKey = generateDictCacheKey(code);
                CacheUtil.remove(cacheKey);
                log.info("已清除字典缓存，字典编码: {}", code);
                return ApiResult.success("已清除指定字典缓存");
            } else {
                int count = CacheUtil.removeByPattern("dictionary:*");
                log.info("已清除所有字典缓存，共 {} 项", count);
                return ApiResult.success("已清除所有字典缓存，共 " + count + " 项");
            }
        } catch (Exception e) {
            log.error("清除字典缓存失败，错误信息: {}", e.getMessage(), e);
            return ApiResult.fail("清除缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 生成字典缓存键
     * 
     * @param dictCode 字典编码
     * @return 缓存键
     */
    private String generateDictCacheKey(String dictCode) {
        return "dictionary:" + dictCode;
    }

    /**
     * 字典项返回对象
     *
     * @author system
     */
    @Data
    public static class DictionaryItem implements Serializable {
        
        private static final long serialVersionUID = 1L;

        private String itemCode;
        private String itemName;

        public DictionaryItem(String itemCode, String itemName) {
            this.itemCode = itemCode;
            this.itemName = itemName;
        }
    }
}