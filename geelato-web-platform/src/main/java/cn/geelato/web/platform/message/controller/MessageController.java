package cn.geelato.web.platform.message.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.message.entity.UniMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@ApiRestController("/message/uni")
@Slf4j
public class MessageController extends BaseController {
    @RequestMapping(value = "/send", method = RequestMethod.POST)
    public ApiResult<?> send(@RequestBody UniMsg msg) {
        return null;
    }
}
