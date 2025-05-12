package cn.geelato.web.platform.m.example;


import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.oss.OSSResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.common.FileHelper;
import cn.geelato.web.platform.m.BaseController;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


@ApiRestController("/file")
@Slf4j
public class FileExampleController extends BaseController {

    @Autowired
    private FileHelper fileHelper;
    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public ApiResult<String> example(@RequestParam("file") MultipartFile file) {
        try {
            OSSResult ossResult=fileHelper.putFile(file);
            return ApiResult.success(JSONObject.toJSONString(ossResult));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @RequestMapping(value = "/download", method = RequestMethod.GET)
    public void example(@RequestParam("objectName") String objectName) throws IOException {
        OSSResult ossResult=fileHelper.getFile(objectName);
        if(ossResult.getSuccess()) {
            this.response.setContentType(ossResult.getOssFile().getFileMeta().getFileContentType());
            this.response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + ossResult.getOssFile().getFileMeta().getFileName() + "\"");
            InputStream inputStream = ossResult.getOssFile().getFileMeta().getFileInputStream();
            OutputStream outputStream = response.getOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            inputStream.close();
            outputStream.close();
        }
    }
}
