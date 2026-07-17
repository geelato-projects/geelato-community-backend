package cn.geelato.web.platform.srv.base;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.meta.Dict;
import cn.geelato.meta.DictItem;
import cn.geelato.orm.Filter;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.Order;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApiRestController("/system/dictionary")
@Slf4j
public class DictionaryController extends BaseController {

    @RequestMapping(value = "/{code}", method = RequestMethod.GET)
    public ApiResult<List<DictItem>> getByCode(@PathVariable String code) {
        try {
            if (Strings.isBlank(code)) {
                return ApiResult.success(new ArrayList<>());
            }

            Dict dict = MetaFactory.query(Dict.class)
                    .where(
                            Filter.eq("dictCode", code),
                            Filter.eq("enableStatus", 1),
                            Filter.eq("delStatus", 0)
                    )
                    .order(Order.asc("seqNo"))
                    .wrapperResult(this::toDict)
                    .one();
            if (dict == null || Strings.isBlank(dict.getId())) {
                return ApiResult.success(new ArrayList<>());
            }

            List<DictItem> items = MetaFactory.query(DictItem.class)
                    .where(
                            Filter.eq("dictId", dict.getId()),
                            Filter.eq("enableStatus", 1),
                            Filter.eq("delStatus", 0)
                    )
                    .order(Order.asc("seqNo"))
                    .wrapperResult(this::toDictItem)
                    .list();
            return ApiResult.success(items);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    private Dict toDict(Map<String, Object> row) {
        return row == null || row.isEmpty() ? null : JSON.parseObject(JSON.toJSONString(row), Dict.class);
    }

    private DictItem toDictItem(Map<String, Object> row) {
        return row == null || row.isEmpty() ? null : JSON.parseObject(JSON.toJSONString(row), DictItem.class);
    }
}
