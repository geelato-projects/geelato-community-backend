package cn.geelato.web.platform.srv.resolve;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@ApiRestController("/resolve")
public class ResolveController extends BaseController {
    private final ResolveFacade resolveFacade;

    public ResolveController(ResolveFacade resolveFacade) {
        this.resolveFacade = resolveFacade;
    }

    /**
     * 同步执行解析，并直接返回解析结果与步骤明细。
     */
    @RequestMapping(method = RequestMethod.POST)
    public ApiResult<?> resolve(
            @RequestParam(value = "fileId", required = false) String fileId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "biztag", required = false) String biztag,
            @RequestPart(value = "config", required = false) String config
    ) {
        return resolveFacade.resolve(fileId, file, biztag, config, getAppId(), getTenantCode());
    }

    /**
     * 异步提交解析任务，并返回 taskId 供后续轮询。
     */
    @RequestMapping(value = "/submit", method = RequestMethod.POST)
    public ApiResult<?> submit(
            @RequestParam(value = "fileId", required = false) String fileId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "biztag", required = false) String biztag,
            @RequestPart(value = "config", required = false) String config
    ) {
        return resolveFacade.submit(fileId, file, biztag, config, getAppId(), getTenantCode());
    }

    /**
     * 查询异步解析任务状态、步骤与最终结果。
     */
    @RequestMapping(value = "/task/{taskId}", method = RequestMethod.GET)
    public ApiResult<?> task(@PathVariable String taskId) {
        return resolveFacade.task(taskId);
    }
}
