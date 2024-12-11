package cn.geelato.web.platform.m.example;


import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;


@ApiRestController("/file")
@Slf4j
public class FileExampleController extends BaseController {

    @RequestMapping(value = "/example", method = RequestMethod.POST)
    public ApiResult<String> example(@RequestParam("file") MultipartFile file) {
        return null;
    }
}
