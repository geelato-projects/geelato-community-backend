package cn.geelato.web.platform.m.base.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.meta.DictItem;
import org.apache.logging.log4j.util.Strings;
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
     * 批量插入和更新字典项
     * <p>
     * 根据给定的字典ID、父ID和字典项列表，进行批量插入和更新操作。
     *
     * @param dictId   字典ID，用于标识要操作的字典
     * @param parentId 父ID，用于指定字典项的父级分类
     * @param forms    字典项列表，包含要插入或更新的字典项信息
     * @throws RuntimeException 如果字典ID为空，则抛出运行时异常，并返回更新失败的错误信息
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
                    mItem.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
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
                item.setDelStatus(ColumnDefault.DEL_STATUS_VALUE);
                if (Strings.isBlank(item.getId()) && Strings.isBlank(item.getTenantCode())) {
                    item.setTenantCode(getSessionTenantCode());
                }
                dao.save(item);
            }
        }
    }

    /**
     * 更新一条数据
     * <p>
     * 更新指定的字典项数据，并处理其启用状态对子项的影响。
     *
     * @param model 要更新的字典项数据
     * @return 返回更新后的数据映射
     */
    public Map updateModel(DictItem model) {
        model.setDelStatus(ColumnDefault.DEL_STATUS_VALUE);
        Map<String, Object> map = dao.save(model);

        if (EnableStatusEnum.DISABLED.getValue() == model.getEnableStatus()) {
            Map<String, Object> params = new HashMap<>();
            params.put("dictId", model.getDictId());
            List<DictItem> list = queryModel(DictItem.class, params);
            if (list != null && !list.isEmpty()) {
                List<DictItem> childs = childIteration(list, model.getId());
                if (childs != null && !childs.isEmpty()) {
                    for (DictItem item : childs) {
                        item.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
                        super.isDeleteModel(item);
                    }
                }
            }
        }
        return map;
    }

    /**
     * 逻辑删除，并删除子集
     * <p>
     * 该方法用于逻辑删除指定的字典项及其子集。
     *
     * @param model 要删除的字典项模型对象
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
                    item.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
                    super.isDeleteModel(item);
                }
            }
        }
    }

    /**
     * 基础逻辑删除
     * <p>
     * 该方法用于执行字典项的逻辑删除操作，即将字典项的启用状态设置为禁用。
     *
     * @param model 要进行逻辑删除的字典项模型对象
     */
    public void isDeleteDictItem(DictItem model) {
        model.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
        super.isDeleteModel(model);
    }


    /**
     * 递归查找子节点
     * <p>
     * 根据给定的节点列表和父节点ID，递归查找所有子节点，并将它们添加到结果列表中。
     *
     * @param list 节点列表，包含所有节点的信息
     * @param pid  父节点ID，用于指定要查找的子节点的父节点
     * @return 返回包含所有子节点的列表
     */
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
