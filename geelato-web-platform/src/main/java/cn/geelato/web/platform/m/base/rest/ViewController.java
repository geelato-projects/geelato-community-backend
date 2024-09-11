package cn.geelato.web.platform.m.base.rest;

import cn.geelato.core.constants.MediaTypes;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.ViewManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.view.ViewMeta;
import cn.geelato.lang.api.ApiMetaResult;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.base.service.ViewService;
import cn.geelato.web.platform.m.security.entity.DataItems;
import com.alibaba.fastjson2.JSONArray;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
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
    public ApiPagedResult<?> pageQuery(@PathVariable("view_name") String viewName, HttpServletRequest req) {
        try {
            int pageNum = Strings.isNotBlank(req.getParameter("current")) ? Integer.parseInt(req.getParameter("current")) : -1;
            int pageSize = Strings.isNotBlank(req.getParameter("pageSize")) ? Integer.parseInt(req.getParameter("pageSize")) : -1;
            ViewMeta viewMeta = ViewManager.singleInstance().getByViewName(viewName);
            EntityMeta entityMeta = MetaManager.singleInstance().get(viewMeta.getSubjectEntity());
            Map<String, Object> params = this.getQueryParameters(entityMeta.getClass(), req);
            List<Map<String, Object>> pageQueryList = viewService.pageQueryModel(entityMeta.getEntityName(), viewName, pageNum, pageSize, params);

            return ApiPagedResult.success(new DataItems(pageQueryList, pageQueryList.size()), pageNum, pageSize, pageQueryList.size(), 999);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = {"/defined/{view_name}"}, method = {RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult export(@PathVariable("view_name") String viewName) {
        ApiMetaResult result = new ApiMetaResult();
        ViewMeta viewMeta = ViewManager.singleInstance().getByViewName(viewName);
        String viewColumnJson = viewMeta.getViewColumn();
        if (!StringUtils.isBlank(viewColumnJson)) {
            JSONArray jsonArray = JSONArray.parse(viewMeta.getViewColumn());
            result.setMeta(jsonArray);
        } else {
            result.setMeta(viewColumnJson);
        }
        return result;
    }
}
