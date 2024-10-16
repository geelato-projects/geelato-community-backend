package cn.geelato.web.platform.m.base.rest;


import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.orm.DaoException;
import cn.geelato.lang.api.ApiMetaResult;
import cn.geelato.lang.api.ApiMultiPagedResult;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.annotation.ApiRuntimeRestController;
import cn.geelato.web.platform.boot.DynamicDatasourceHolder;
import cn.geelato.web.platform.m.BaseController;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;


@ApiRuntimeRestController("/bizData/")
@Slf4j
public class BizDataController extends BaseController {

    @RequestMapping(value = {"list", "list/*"}, method = {RequestMethod.POST, RequestMethod.GET})
    public ApiPagedResult list(@RequestParam(value = "withMeta", defaultValue = "true") boolean withMeta, HttpServletRequest request) {
        String gql = getGql(request, "query");
        return ruleService.queryForMapList(gql, withMeta);
    }


    @RequestMapping(value = {"multiList", "multiList/*"}, method = RequestMethod.POST)
    public ApiMultiPagedResult multiList(@RequestParam(value = "withMeta", defaultValue = "true") boolean withMeta, HttpServletRequest request) {
        String gql = getGql(request, null);
        return ruleService.queryForMultiMapList(gql, withMeta);
    }


    @RequestMapping(value = {"save/{biz}"}, method = RequestMethod.POST)
    public ApiMetaResult save(@PathVariable("biz") String biz, HttpServletRequest request) throws DaoException {
        String gql = getGql(request, "save");
        ApiMetaResult result = new ApiMetaResult();
        result.setData(ruleService.save(biz, gql));
        return result;
    }

    @RequestMapping(value = {"batchSave"}, method = RequestMethod.POST)
    public ApiMetaResult batchSave(HttpServletRequest request) throws DaoException {
        String gql = getGql(request, "batchSave");
        ApiMetaResult result = new ApiMetaResult();
        result.setData(ruleService.batchSave(gql, true));
        return result;
    }

    @RequestMapping(value = {"multiSave"}, method = RequestMethod.POST)
    public ApiMetaResult multiSave(HttpServletRequest request) {
        String gql = getGql(request, "multiSave");
        ApiMetaResult result = new ApiMetaResult();
        result.setData(ruleService.multiSave(gql));
        return result;
    }

    @RequestMapping(value = {"delete/{biz}/{id}"}, method = RequestMethod.POST)
    public ApiMetaResult delete(@PathVariable("biz") String biz, @PathVariable("id") String id) {
        ApiMetaResult result = new ApiMetaResult();
        result.setData(ruleService.delete(biz, id));
        return result;
    }

    @RequestMapping(value = {"delete2/{biz}"}, method = RequestMethod.POST)
    public ApiMetaResult delete(@PathVariable("biz") String biz, HttpServletRequest request) {
        String gql = getGql(request, "delete");
        ApiMetaResult result = new ApiMetaResult();
        result.setData(ruleService.deleteByGql(biz, gql));
        return result;
    }

    @RequestMapping(value = {"tree/{biz}"}, method = RequestMethod.POST)
    @ResponseBody
    public ApiResult treeNodeList(@PathVariable("biz") String biz, @RequestParam String entity, @RequestParam Long treeId, HttpServletRequest request) {
        return ruleService.queryForTreeNodeList(entity, treeId);
    }


    private String getGql(HttpServletRequest request, String type) {
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader br = null;
        try {
            br = request.getReader();
        } catch (IOException e) {
            log.error("未能从httpServletRequest中获取gql的内容", e);
        }
        String str;
        try {
            while ((str = br.readLine()) != null) {
                stringBuilder.append(str);
            }
        } catch (IOException e) {
            log.error("未能从httpServletRequest中获取gql的内容", e);
        }
        String gql = stringBuilder.toString();
        if (type != null) {
            EntityMeta entityMeta = ruleService.resolveEntity(gql, type);
            DynamicDatasourceHolder.setDataSourceKey(entityMeta.getTableMeta().getConnectId());
        }
        return gql;
    }

}
