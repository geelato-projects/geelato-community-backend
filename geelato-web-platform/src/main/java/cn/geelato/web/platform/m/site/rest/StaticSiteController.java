package cn.geelato.web.platform.m.site.rest;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.SqlParams;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.site.entity.FileInfo;
import cn.geelato.web.platform.m.site.entity.StaticSite;
import cn.geelato.web.platform.m.site.service.StaticSiteService;
import cn.geelato.web.platform.m.site.utils.FileSystemTreeBuilder;
import cn.geelato.web.platform.m.site.utils.FolderUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@ApiRestController("/site/statics")
@Slf4j
public class StaticSiteController extends BaseController {
    private static final Class<StaticSite> CLAZZ = StaticSite.class;
    private final StaticSiteService staticSiteService;
    private final FileHandler fileHandler;

    @Autowired
    public StaticSiteController(StaticSiteService staticSiteService, FileHandler fileHandler) {
        this.staticSiteService = staticSiteService;
        this.fileHandler = fileHandler;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return staticSiteService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(staticSiteService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(staticSiteService.getModel(CLAZZ, id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/tree/{id}", method = RequestMethod.GET)
    public ApiResult tree(@PathVariable(required = true) String id) {
        try {
            FileInfo tree = FileSystemTreeBuilder.buildFileSystemTree("/upload", 5);
            return ApiResult.success(tree);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody StaticSite form) {
        try {
            if (Strings.isNotBlank(form.getId())) {
                form = staticSiteService.updateModel(form);
            } else {
                form = staticSiteService.createModel(form);
            }
            return ApiResult.success(form);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            StaticSite model = staticSiteService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
            staticSiteService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody StaticSite form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("code", form.getCode());
            params.put("del_status", String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
            params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(staticSiteService.validate("platform_static_site", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }


    @RequestMapping(value = "/treeNode", method = RequestMethod.POST)
    public ApiResult treeNode(@RequestBody Map<String, Object> requestBody) {
        try {
            String rootPath = Objects.toString(requestBody.get("path"), "");
            String appId = Objects.toString(requestBody.get("appId"), getAppId());
            String tenantCode = Objects.toString(requestBody.get("tenantCode"), getTenantCode());
            if (StringUtils.isBlank(rootPath)) {
                if (StringUtils.isAnyBlank(appId, tenantCode)) {
                    throw new RuntimeException("appId和tenantCode不能为空");
                }
                List<StaticSite> staticSiteList = staticSiteService.queryModel(CLAZZ, SqlParams.map("appId", appId, "tenantCode", tenantCode), "updateAt desc");
                return ApiResult.success(StaticSite.buildTreeNodeDataList(staticSiteList));
            } else {
                Set<FileInfo> fileInfos = FolderUtils.getRootFolders(rootPath);
                return ApiResult.success(FileInfo.buildTreeNodeDataList(fileInfos));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryFile", method = RequestMethod.POST)
    public ApiResult queryFile(@RequestBody Map<String, Object> requestBody) {
        try {
            String rootPath = Objects.toString(requestBody.get("path"), "");
            String type = Objects.toString(requestBody.get("type"), "all");
            if (StringUtils.isBlank(rootPath)) {
                throw new RuntimeException("path不能为空");
            }
            Set<FileInfo> fileInfos = FolderUtils.getRootFiles(rootPath, type);
            FileInfo.sortFileInfos(fileInfos);
            return ApiResult.success(fileInfos);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/batchPack", method = RequestMethod.POST)
    public ApiResult batchPack(@RequestBody Map<String, Object> requestBody) {
        try {
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/batchDelete", method = RequestMethod.POST)
    public ApiResult batchDelete(@RequestBody Map<String, Object> requestBody) {
        try {
            String path = Objects.toString(requestBody.get("path"), "");
            List<String> delPaths = cn.geelato.utils.StringUtils.toListDr(path);
            if (delPaths.isEmpty()) {
                throw new RuntimeException("path不能为空");
            }
            for (String delPath : delPaths) {
                FolderUtils.delete(new File(delPath));
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/download", method = RequestMethod.POST)
    public void download(@RequestBody Map<String, Object> requestBody) throws IOException {
        String path = Objects.toString(requestBody.get("path"), "");
        boolean isPreview = Objects.isNull(requestBody.get("isPreview")) ? false : Boolean.parseBoolean(requestBody.get("isPreview").toString());
        if (StringUtils.isBlank(path)) {
            throw new RuntimeException("path不能为空");
        }
        File file = new File(path);
        if (Files.isDirectory(file.toPath())) {
            throw new RuntimeException("path不是文件");
        }
        fileHandler.download(file, file.getName(), isPreview, request, response, null);
    }

    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public ApiResult upload(@RequestBody Map<String, Object> requestBody) {
        try {
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/updateName", method = RequestMethod.POST)
    public ApiResult updateName(@RequestBody Map<String, Object> requestBody) {
        try {
            String path = Objects.toString(requestBody.get("path"), "");
            String name = Objects.toString(requestBody.get("name"), "");
            if (StringUtils.isAnyBlank(path, name)) {
                throw new RuntimeException("path和name不能为空");
            }
            FolderUtils.renameFileOrFolder(path, name);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createFolder", method = RequestMethod.POST)
    public ApiResult createFolder(@RequestBody Map<String, Object> requestBody) {
        try {
            String path = Objects.toString(requestBody.get("path"), "");
            String name = Objects.toString(requestBody.get("name"), "");
            if (StringUtils.isAnyBlank(path, name)) {
                throw new RuntimeException("path和name不能为空");
            }
            FolderUtils.create(path, name);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

}
