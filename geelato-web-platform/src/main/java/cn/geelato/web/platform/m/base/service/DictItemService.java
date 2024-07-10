package cn.geelato.web.platform.m.base.service;

import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.constants.ApiErrorMsg;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.web.platform.m.base.entity.DictItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Component
public class DictItemService extends BaseSortableService {

    /**
     * 批量插入和更新
     *
     * @param dictId
     * @param parentId
     * @param forms
     */
    public void batchCreateOrUpdate(String dictId, String parentId, List<DictItem> forms) {
        if (Strings.isBlank(dictId)) {
            throw new RuntimeException(ApiErrorMsg.UPDATE_FAIL);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("dictId", dictId);
        params.put("pid", parentId);
        List<DictItem> itemList = queryModel(DictItem.class, params);
        // 删除
        if (itemList != null && !itemList.isEmpty()) {
            for (DictItem mItem : itemList) {
                boolean isExist = false;
                if (forms != null && !forms.isEmpty()) {
                    for (DictItem fItem : forms) {
                        if (mItem.getId().equals(fItem.getId())) {
                            isExist = true;
                            break;
                        }
                    }
                }
                if (!isExist) {
                    mItem.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
                    isDeleteModel(mItem);
                }
            }
        }
        // 保存、更新
        if (forms != null && !forms.isEmpty()) {
            for (int i = 0; i < forms.size(); i++) {
                DictItem item = forms.get(i);
                item.setSeqNo(i + 1);
                item.setDictId(Strings.isBlank(item.getDictId()) ? dictId : item.getDictId());
                item.setDelStatus(DeleteStatusEnum.NO.getCode());
                if (Strings.isBlank(item.getId()) && Strings.isBlank(item.getTenantCode())) {
                    item.setTenantCode(getSessionTenantCode());
                }
                dao.save(item);
            }
        }
    }

    /**
     * 更新一条数据
     *
     * @param model 实体数据
     * @return
     */
    public Map updateModel(DictItem model) {
        model.setDelStatus(DeleteStatusEnum.NO.getCode());
        Map<String, Object> map = dao.save(model);

        if (EnableStatusEnum.DISABLED.getCode() == model.getEnableStatus()) {
            Map<String, Object> params = new HashMap<>();
            params.put("dictId", model.getDictId());
            List<DictItem> list = queryModel(DictItem.class, params);
            if (list != null && !list.isEmpty()) {
                List<DictItem> childs = childIteration(list, model.getId());
                if (childs != null && !childs.isEmpty()) {
                    for (DictItem item : childs) {
                        item.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
                        super.isDeleteModel(item);
                    }
                }
            }
        }
        return map;
    }

    /**
     * 逻辑删除，并删除子集
     *
     * @param model
     */
    public void isDeleteModelAndChild(DictItem model) {
        List<DictItem> childs = new ArrayList<>();
        childs.add(model);

        Map<String, Object> params = new HashMap<>();
        params.put("dictId", model.getDictId());
        List<DictItem> list = queryModel(DictItem.class, params);
        if (list != null && !list.isEmpty()) {
            childs.addAll(childIteration(list, model.getId()));
            if (childs != null && !childs.isEmpty()) {
                for (DictItem item : childs) {
                    item.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
                    super.isDeleteModel(item);
                }
            }
        }
    }

    /**
     * 基础逻辑删除
     *
     * @param model
     */
    public void isDeleteDictItem(DictItem model) {
        model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
        super.isDeleteModel(model);
    }


    private List<DictItem> childIteration(List<DictItem> list, String pid) {
        List<DictItem> result = new ArrayList<>();
        for (DictItem item : list) {
            if (Strings.isNotBlank(item.getPid()) && item.getPid().equals(pid)) {
                // 如果当前节点是指定id的父节点，则将其添加到结果中并继续递归查找其子集
                result.add(item);
                result.addAll(childIteration(list, item.getId()));
                // 继续递归查找子集并添加到结果中
            }
        }

        return result;
    }
}
