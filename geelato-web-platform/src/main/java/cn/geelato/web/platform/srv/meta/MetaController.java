package cn.geelato.web.platform.srv.meta;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.lang.api.ApiMetaResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.base.service.DownloadService;
import cn.geelato.web.platform.srv.meta.codegen.EntityJavaSourceGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@DesignTimeApiRestController("/meta")
@Slf4j
public class MetaController extends BaseController {

    private final MetaManager metaManager = MetaManager.singleInstance();

    @Autowired
    private EntityJavaSourceGenerator entityJavaSourceGenerator;
    @Autowired
    private DownloadService downloadService;

    /**
     * 获取数据定义信息，即元数据信息
     *
     * @param entity 实体名称
     */
    @RequestMapping(value = {"/defined/{entity}"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> defined(@PathVariable("entity") String entity) {
        if (metaManager.containsEntity(entity)) {
            return ApiMetaResult.success(metaManager.getByEntityName(entity).getAllSimpleFieldMetas());
        }
        return ApiMetaResult.fail("not found meta defined");
    }

    @RequestMapping(value = {"/fullDefined/{entity}"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> fullDefined(@PathVariable("entity") String entity) {
        if (metaManager.containsEntity(entity)) {
            return ApiMetaResult.success(metaManager.getByEntityName(entity));
        }
        return ApiMetaResult.fail("not found meta defined");
    }

    /**
     * 获取实体名称列表
     */
    @RequestMapping(value = {"/entityNames"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> entityNames() {
        return ApiResult.success(metaManager.getAllEntityNames());
    }

    /**
     * 获取指定应用下的精简版实体元数据信息列表
     */
    @RequestMapping(value = {"/entityLiteMetas"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> queryLiteEntities() {
        return ApiResult.success(metaManager.getAllEntityLiteMetas());
    }

    /**
     * 查询所有同时存在于 Java 类源与在线DB源的冲突实体及其字段级差异明细。
     * <p>
     * 用于诊断"Java实体与在线实体重复导致CRUD无法构造合适SQL"的问题。
     *
     * @return key=entityName，value=差异明细
     */
    @RequestMapping(value = {"/conflicts"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> conflicts() {
        return ApiResult.success(metaManager.getAllConflicts());
    }

    /**
     * 查询单个冲突实体的字段级差异明细。
     *
     * @param entity 实体名称
     * @return 差异明细；若不存在冲突则返回空差异
     */
    @RequestMapping(value = {"/conflicts/{entity}"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> conflict(@PathVariable("entity") String entity) {
        return ApiResult.success(metaManager.compareEntitySources(entity));
    }

    /**
     * 同步指定实体的元数据，使主缓存与在线DB定义一致。
     * <p>
     * 当前实现：重新从数据库加载该实体的在线定义并刷新主缓存（db-to-class 方向，
     * 即使 Java 类仍存在，配合 conflict-strategy=DATABASE 也会以在线DB定义为准覆盖）。
     * 同步完成后返回最新的差异校验结果。
     *
     * @param entity    实体名称
     * @param direction 同步方向，目前仅支持 db-to-class（默认）
     * @return 同步后的差异明细
     */
    @RequestMapping(value = {"/sync/{entity}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> sync(@PathVariable("entity") String entity,
                             @RequestParam(value = "direction", required = false, defaultValue = "db-to-class") String direction) {
        try {
            if (!"db-to-class".equalsIgnoreCase(direction)) {
                return ApiResult.fail("暂不支持的同步方向：" + direction + "，当前仅支持 db-to-class");
            }
            metaManager.refreshDBMeta(entity);
            return ApiResult.success(metaManager.compareEntitySources(entity));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 反向生成实体的 Java 源码（预览，不入库、不落盘）。
     * <p>
     * 把 platform_dev_table/platform_dev_column 在线定义（或基于 Java 类的 EntityMeta）
     * 反向构造为带 @Entity/@Col/@Title/@Id 注解的 .java 源码，供开发者预览/复制后手工落盘。
     *
     * @param entity     实体名称
     * @param packageName 生成类所属包名，可选，默认 cn.geelato.meta
     * @return {className, fileName, packageName, source}
     */
    @RequestMapping(value = {"/codegen/{entity}"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> codegen(@PathVariable("entity") String entity,
                                @RequestParam(value = "packageName", required = false) String packageName) {
        try {
            EntityMeta em = metaManager.getByEntityName(entity);
            if (em == null) {
                return ApiResult.fail("not found meta defined");
            }
            String pkg = StringUtils.isBlank(packageName) ? EntityJavaSourceGenerator.DEFAULT_PACKAGE_NAME : packageName.trim();
            String source = entityJavaSourceGenerator.generate(entity, pkg);
            String className = EntityJavaSourceGenerator.toClassName(em);
            Map<String, Object> result = new HashMap<>();
            result.put("className", className);
            result.put("fileName", className + ".java");
            result.put("packageName", pkg);
            result.put("source", source);
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 下载反向生成的 Java 源码文件（不落盘，直接流式下载）。
     *
     * @param entity     实体名称
     * @param packageName 生成类所属包名，可选，默认 cn.geelato.meta
     */
    @RequestMapping(value = {"/codegen/{entity}/download"}, method = {RequestMethod.GET})
    public void codegenDownload(@PathVariable("entity") String entity,
                                @RequestParam(value = "packageName", required = false) String packageName,
                                HttpServletRequest request, HttpServletResponse response) {
        try {
            EntityMeta em = metaManager.getByEntityName(entity);
            if (em == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "not found meta defined");
                return;
            }
            String pkg = StringUtils.isBlank(packageName) ? EntityJavaSourceGenerator.DEFAULT_PACKAGE_NAME : packageName.trim();
            String source = entityJavaSourceGenerator.generate(entity, pkg);
            String className = EntityJavaSourceGenerator.toClassName(em);
            downloadService.downloadFile(
                    new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),
                    className + ".java",
                    false, request, response, "text/plain");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            } catch (Exception ignored) {
            }
        }
    }


}

