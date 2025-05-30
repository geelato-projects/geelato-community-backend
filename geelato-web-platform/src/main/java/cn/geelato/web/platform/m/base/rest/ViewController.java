package cn.geelato.web.platform.m.base.rest;

import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.ViewManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.view.ViewMeta;
import cn.geelato.lang.api.ApiMetaResult;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.DataItems;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.service.ViewService;
import com.alibaba.fastjson2.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Map;

@ApiRestController("/view")
@Slf4j
public class ViewController extends BaseController {
    private final ViewService viewService;

    @Autowired
    public ViewController(ViewService viewService) {
        this.viewService = viewService;
    }

    @RequestMapping(value = {"/pageQuery/{view_name}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiPagedResult<?> pageQuery(@PathVariable("view_name") String viewName) {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            ViewMeta viewMeta = ViewManager.singleInstance().getByViewName(viewName);
            EntityMeta entityMeta = MetaManager.singleInstance().get(viewMeta.getSubjectEntity());
            Map<String, Object> params = this.getQueryParameters(entityMeta.getClass());
            List<Map<String, Object>> pageQueryList = viewService.pageQueryModel(entityMeta.getEntityName(), viewName, pageQueryRequest.getPageNum(), pageQueryRequest.getPageSize(), params);

            return ApiPagedResult.success(new DataItems(pageQueryList, pageQueryList.size()), pageQueryRequest.getPageNum(), pageQueryRequest.getPageSize(), pageQueryList.size(), 999);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = {"/defined/{view_name}"}, method = {RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult export(@PathVariable("view_name") String viewName) {
        ViewMeta viewMeta = ViewManager.singleInstance().getByViewName(viewName);
        String viewColumnJson = viewMeta.getViewColumn();
        if (!StringUtils.isBlank(viewColumnJson)) {
            JSONArray jsonArray = JSONArray.parse(viewMeta.getViewColumn());
            return ApiMetaResult.success(jsonArray);
        } else {
            return ApiMetaResult.success(viewColumnJson);
        }
    }
}
