package cn.geelato.web.platform.m.base.rest;


import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.entity.FileInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

/**
 * 文件上传下载管理
 *
 * @author itechgee@126.com
 */
@ApiRestController("/file/")
@Slf4j
public class FileController extends BaseController {

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");
    private final Random random = new Random();
    @Value(value = "${geelato.file.root.path}")
    protected String fileRootPath;

    /**
     * 处理文件上传
     *
     * @return 上传的字节数，-1表示上传失败
     */
    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public ApiResult uploadFile(@RequestParam("file") MultipartFile file) {
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        String relativePath = sdf.format(new Date());
        String filePath = this.fileRootPath + "\\upload\\" + relativePath + "\\";
        String fileType = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        String savedFileName = System.currentTimeMillis() + "" + random.nextInt(9) + random.nextInt(9) + "." + fileType;


        ApiResult apiResult = new ApiResult();
        try {
            int size = file.getBytes().length;
            saveToFileSystem(file.getBytes(), filePath, savedFileName);
            // TODO 事务
            FileInfo fileInfo = new FileInfo();
            fileInfo.setName(originalFilename.substring(0, originalFilename.lastIndexOf(".")));
            fileInfo.setSavedName(savedFileName);
            fileInfo.setRelativePath(relativePath);
            fileInfo.setFileType(fileType);
            fileInfo.setSize(size);
            fileInfo.setDescription(null);
            apiResult.setData(dao.save(fileInfo));
            apiResult.success();
            apiResult.setMsg("上传文件成功！");
        } catch (IOException e) {
            log.error("上传文件失败！", e);
            apiResult.error();
            apiResult.setMsg("上传文件失败！");
        }
        return apiResult;
    }

    /**
     *
     */
    private void saveToFileSystem(byte[] file, String filePath, String fileName) throws IOException {
        File targetFile = new File(filePath);
        if (!targetFile.exists()) {
            targetFile.mkdirs();
        }
        FileOutputStream out = new FileOutputStream(filePath + fileName);
        out.write(file);
        out.flush();
        out.close();
    }

}
