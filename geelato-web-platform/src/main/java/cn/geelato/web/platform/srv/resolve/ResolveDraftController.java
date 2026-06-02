package cn.geelato.web.platform.srv.resolve;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.resolve.model.ResolveDraftUpdateRequest;
import cn.geelato.web.platform.srv.BaseController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@ApiRestController("/resolve/draft")
public class ResolveDraftController extends BaseController {
    private final ResolveDraftFacade draftFacade;

    public ResolveDraftController(ResolveDraftFacade draftFacade) {
        this.draftFacade = draftFacade;
    }

    @RequestMapping(method = RequestMethod.POST)
    public ApiResult<?> create(
            @RequestParam(value = "fileId", required = false) String fileId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "biztag", required = false) String biztag,
            @RequestPart(value = "config", required = false) String config
    ) {
        return draftFacade.createDraft(fileId, file, biztag, config, getAppId(), getTenantCode());
    }

    @RequestMapping(value = "/{draftId}", method = RequestMethod.GET)
    public ApiResult<?> get(@PathVariable String draftId) {
        return draftFacade.getDraft(draftId);
    }

    @RequestMapping(value = "/{draftId}", method = RequestMethod.PUT)
    public ApiResult<?> update(@PathVariable String draftId, @RequestBody ResolveDraftUpdateRequest req) {
        return draftFacade.updateDraft(draftId, req);
    }

    @RequestMapping(value = "/{draftId}/confirm", method = RequestMethod.POST)
    public ApiResult<?> confirm(@PathVariable String draftId) {
        return draftFacade.confirm(draftId);
    }
}

