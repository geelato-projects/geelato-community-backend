package cn.geelato.web.platform.m.base.service;

import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.constants.ApiErrorMsg;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.web.platform.m.base.entity.Dict;
import cn.geelato.web.platform.m.base.entity.DictItem;
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
     *
     * @param model
     */
    public void isDeleteModel(Dict model) {
        model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
        super.isDeleteModel(model);
        // 清理 字典项
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
     *
     * @param model
     * @return
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
