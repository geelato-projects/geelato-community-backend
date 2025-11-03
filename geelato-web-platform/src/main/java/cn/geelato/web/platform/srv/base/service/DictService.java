package cn.geelato.web.platform.srv.base.service;

import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.meta.Dict;
import cn.geelato.meta.DictItem;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Component
public class DictService extends BaseSortableService {
    @Autowired
    private DictItemService dictItemService;

    /**
     * 逻辑删除
     * <p>
     * 将给定的字典模型对象标记为禁用状态，并清理与之关联的字典项。
     *
     * @param model 要进行逻辑删除的字典模型对象
     */
    public void isDeleteModel(Dict model) {
        model.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
        super.isDeleteModel(model);
        Map<String, Object> params = new HashMap<>();
        params.put("dictId", model.getId());
        List<DictItem> iList = dictItemService.queryModel(DictItem.class, params);
        if (iList != null) {
            for (DictItem iModel : iList) {
                dictItemService.isDeleteDictItem(iModel);
            }
        }
    }

    /**
     * 更新数据字典
     * <p>
     * 更新指定ID的数据字典信息，并处理应用变更。
     *
     * @param model 包含要更新的数据字典信息的模型对象
     * @return 返回更新后的数据字典模型对象
     */
    public Dict updateModel(Dict model) {
        Dict source = getModel(Dict.class, model.getId());
        Assert.notNull(source, ApiErrorMsg.IS_NULL);
        Dict dict = super.updateModel(model);
        // 应用变更
        if ((Strings.isNotBlank(source.getAppId()) && !source.getAppId().equals(dict.getAppId())) ||
                (Strings.isNotBlank(dict.getAppId()) && !dict.getAppId().equals(source.getAppId()))) {
            Map<String, Object> params = new HashMap<>();
            params.put("dictId", dict.getId());
            List<DictItem> iList = dictItemService.queryModel(DictItem.class, params);
            if (iList != null) {
                for (DictItem iModel : iList) {
                    iModel.setAppId(dict.getAppId());
                    dictItemService.updateModel(iModel);
                }
            }
        }

        return dict;
    }
}
