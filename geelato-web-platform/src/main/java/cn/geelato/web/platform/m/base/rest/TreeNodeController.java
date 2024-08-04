package cn.geelato.web.platform.m.base.rest;

import cn.geelato.web.platform.m.base.entity.TreeNode;
import cn.geelato.web.platform.m.base.service.TreeNodeService;
import jakarta.servlet.http.HttpServletRequest;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.core.gql.parser.PageQueryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Controller
@RequestMapping(value = "/api/treeNode")
public class TreeNodeController extends BaseController {
    private static final Class<TreeNode> CLAZZ = TreeNode.class;
    private final Logger logger = LoggerFactory.getLogger(DictController.class);
    @Autowired
    private TreeNodeService treeNodeService;

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult<List<TreeNode>> query(HttpServletRequest req) {
        ApiResult<List<TreeNode>> result = new ApiResult<>();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            result.setData(treeNodeService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    @ResponseBody
    public ApiResult<TreeNode> isDelete(@PathVariable(required = true) String id) {
        ApiResult<TreeNode> result = new ApiResult<>();
        try {
            TreeNode model = treeNodeService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            treeNodeService.isDeleteModel(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }
}
