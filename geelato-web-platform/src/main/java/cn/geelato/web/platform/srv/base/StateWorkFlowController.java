package cn.geelato.web.platform.srv.base;

import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.event.UpgradeStateWorkFlowEvent;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.common.event.EventPublisher;
import cn.geelato.web.common.annotation.ApiRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * 状态工作流控制器
 * 用于处理工作流定义变更的通知
 */
@ApiRestController("/stateWorkFlow")
@Slf4j
public class StateWorkFlowController extends BaseController {

    /**
     * 通知工作流定义更新
     * 由前端在保存完工作流之后调用，通知所有客户端更新工作流配置
     *
     * @param workFlowId 工作流ID
     * @param extendId 扩展ID
     * @return 操作结果
     */
    @RequestMapping(value = {"/notifyUpdate/{procDefId}"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult notifyUpdate(@PathVariable String procDefId) {
        try {
            EventPublisher.publish(new UpgradeStateWorkFlowEvent(this, procDefId));
            return ApiResult.success("工作流更新通知已发送");
        } catch (Exception e) {
            log.error("通知状态工作流更新出错！", e);
            return ApiResult.fail("通知状态机工作流更新出错！" + e.getMessage());
        }
    }
}