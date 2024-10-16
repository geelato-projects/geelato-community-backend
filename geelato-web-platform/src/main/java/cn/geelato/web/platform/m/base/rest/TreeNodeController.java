package cn.geelato.web.platform.m.base.rest;

import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.entity.TreeNode;
import cn.geelato.web.platform.m.base.service.TreeNodeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController("/treeNode")
@Slf4j
public class TreeNodeController extends BaseController {
    private static final Class<TreeNode> CLAZZ = TreeNode.class;
    private final TreeNodeService treeNodeService;

    @Autowired
    public TreeNodeController(TreeNodeService treeNodeService) {
        this.treeNodeService = treeNodeService;
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult<List<TreeNode>> query(HttpServletRequest req) {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            return ApiResult.success(treeNodeService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            TreeNode model = treeNodeService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            treeNodeService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }
}
