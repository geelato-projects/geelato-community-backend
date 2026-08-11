package cn.geelato.web.platform.srv.ocr.invoice;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.ocr.invoice.entity.InvoiceOcrResult;
import cn.geelato.web.platform.srv.ocr.invoice.service.InvoiceOcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 发票 OCR 接口（阶段一：宿主直接实现）。
 * <p>路由前缀 {@code /invoice/ocr}（项目无 /api 前缀）。</p>
 *
 * @author geelato
 */
@ApiRestController(value = "/ocr/invoice")
@Slf4j
public class InvoiceOcrController extends BaseController {

    private final InvoiceOcrService invoiceOcrService;

    @Autowired
    public InvoiceOcrController(InvoiceOcrService invoiceOcrService) {
        this.invoiceOcrService = invoiceOcrService;
    }

    /**
     * 图片/扫描件直传识别。
     *
     * @param file 发票图片（jpg/png 等，multipart/form-data）
     * @return 结构化识别结果
     */
    @PostMapping("/recognize")
    public ApiResult<InvoiceOcrResult> recognize(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ApiResult.fail("文件不能为空");
        }
        return ApiResult.success(invoiceOcrService.recognize(file.getBytes()));
    }

    /**
     * 通过附件 fileId 识别（先用 /upload/file 上传拿 fileId）。
     *
     * @param body {"fileId": "..."}
     */
    @PostMapping("/recognize/fileId")
    public ApiResult<InvoiceOcrResult> recognizeByFileId(@RequestBody Map<String, Object> body) {
        String fileId = body == null ? null : String.valueOf(body.get("fileId"));
        if (StringUtils.isBlank(fileId) || "null".equals(fileId)) {
            return ApiResult.fail("fileId 不能为空");
        }
        return ApiResult.success(invoiceOcrService.recognizeByFileId(fileId));
    }

    /**
     * 引擎健康检查。
     */
    @PostMapping("/health")
    public ApiResult<Boolean> health() {
        return ApiResult.success(invoiceOcrService.healthCheck());
    }
}
