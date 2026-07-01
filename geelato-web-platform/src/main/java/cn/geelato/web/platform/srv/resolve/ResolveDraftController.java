package cn.geelato.web.platform.srv.resolve;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import cn.geelato.web.platform.resolve.model.ResolveDraftUpdateRequest;
import cn.geelato.web.platform.srv.BaseController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@DesignTimeApiRestController("/resolve/draft")
public class ResolveDraftController extends BaseController {
    private final ResolveDraftFacade draftFacade;

    public ResolveDraftController(ResolveDraftFacade draftFacade) {
        this.draftFacade = draftFacade;
    }

    /**
     * 创建解析草稿，返回标准化字段、匹配建议与步骤明细。
     */
    @RequestMapping(method = RequestMethod.POST)
    public ApiResult<?> create(
            @RequestParam(value = "fileId", required = false) String fileId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "biztag", required = false) String biztag,
            @RequestPart(value = "config", required = false) String config
    ) {
        return draftFacade.createDraft(fileId, file, biztag, config, getAppId(), getTenantCode());
    }

    /**
     * 按 draftId 查询已生成的解析草稿。
     */
    @RequestMapping(value = "/{draftId}", method = RequestMethod.GET)
    public ApiResult<?> get(@PathVariable String draftId) {
        return draftFacade.getDraft(draftId);
    }

    /**
     * 更新草稿中允许人工修正的字段和值。
     */
    @RequestMapping(value = "/{draftId}", method = RequestMethod.PUT)
    public ApiResult<?> update(@PathVariable String draftId, @RequestBody ResolveDraftUpdateRequest req) {
        return draftFacade.updateDraft(draftId, req);
    }

    /**
     * 确认草稿并触发后续持久化处理。
     */
    @RequestMapping(value = "/{draftId}/confirm", method = RequestMethod.POST)
    public ApiResult<?> confirm(@PathVariable String draftId) {
        return draftFacade.confirm(draftId);
    }
}
