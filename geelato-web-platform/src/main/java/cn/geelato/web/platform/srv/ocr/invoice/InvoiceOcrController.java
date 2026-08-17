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
 * 发票 OCR 接口（阶段一：宿主直接实现，输出对齐 GB/T 42965.1-2023）。
 * <p>路由前缀 {@code /ocr/invoice}（项目无 /api 前缀）。
 * 按文件后缀自动分流：{@code .pdf} 走 iText 文本层提取（电子发票 PDF 无识别误差），
 * 图片走 RapidOCR4j 识别。项目清单（明细行）默认不解析，调用方传 {@code parseItems=true} 时解析。</p>
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
     * 发票文件直传识别（按后缀自动分流：.pdf 走文本层提取，图片走 OCR）。
     *
     * @param file       发票文件（pdf/jpg/png 等，multipart/form-data）
     * @param parseItems 是否解析项目清单（明细行），默认 false
     * @return 结构化识别结果（发票号码/开票日期/开票金额/备注/购销方等，标准字段见 InvoiceOcrResult）
     */
    @PostMapping("/recognize")
    public ApiResult<InvoiceOcrResult> recognize(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(value = "parseItems", required = false, defaultValue = "false") boolean parseItems) throws IOException {
        if (file == null || file.isEmpty()) {
            return ApiResult.fail("文件不能为空");
        }
        return ApiResult.success(invoiceOcrService.recognize(file.getBytes(), file.getOriginalFilename(), parseItems));
    }

    /**
     * 通过附件 fileId 识别（先用 /upload/file 上传拿 fileId）。
     *
     * @param body {"fileId": "...", "parseItems": true（可选，默认 false）}
     */
    @PostMapping("/recognize/fileId")
    public ApiResult<InvoiceOcrResult> recognizeByFileId(@RequestBody Map<String, Object> body) {
        String fileId = body == null ? null : String.valueOf(body.get("fileId"));
        if (StringUtils.isBlank(fileId) || "null".equals(fileId)) {
            return ApiResult.fail("fileId 不能为空");
        }
        boolean parseItems = Boolean.parseBoolean(String.valueOf(body.getOrDefault("parseItems", "false")));
        return ApiResult.success(invoiceOcrService.recognizeByFileId(fileId, parseItems));
    }

    /**
     * 引擎健康检查。
     */
    @PostMapping("/health")
    public ApiResult<Boolean> health() {
        return ApiResult.success(invoiceOcrService.healthCheck());
    }
}
