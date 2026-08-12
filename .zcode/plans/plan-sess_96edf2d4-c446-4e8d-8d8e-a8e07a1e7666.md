# 阶段一实施计划：宿主直接实现发票 OCR 闭环

> 目标：最快跑通"图片/扫描件 → 结构化发票字段"完整闭环，验证 OCR 识别价值。
> 路线：宿主 `srv/ocr/invoice/` 直接实现，引 rapidocr4j 库（自带 PP-OCRv4 中文模型 + onnxruntime + opencv native），**无需手动下载模型**。

---

## 一、依赖（`geelato-parent/pom.xml` + `geelato-web-platform/pom.xml`）

**geelato-parent/pom.xml** 的 `<properties>` + `<dependencyManagement>` 登记版本：
```xml
<rapidocr4j.version>1.0.3</rapidocr4j.version>
```
```xml
<dependency>
    <groupId>io.github.hzkitty</groupId>
    <artifactId>rapidocr4j</artifactId>
    <version>${rapidocr4j.version}</version>
</dependency>
```

**geelato-web-platform/pom.xml** 引用：
```xml
<dependency>
    <groupId>io.github.hzkitty</groupId>
    <artifactId>rapidocr4j</artifactId>
</dependency>
```
> 传递依赖：`onnxruntime 1.18.0`（85MB，含全平台 native）+ `openpnp opencv 4.6.0-0`（含 native）。这些进宿主 fat jar，部署时注意体积。

---

## 二、新增文件清单（全部在 `geelato-web-platform`，`srv/ocr/invoice/` 子包下）

### 1. 结果 DTO（`srv/ocr/invoice/entity/`）
- **`InvoiceOcrResult.java`** — `@Data` 统一结构化结果：
  - 发票类型/代码/号码/开票日期/校验码
  - 金额(amount)/税额(taxAmount)/价税合计(totalAmount)
  - 购方：buyerName/buyerTaxNo
  - 销方：sellerName/sellerTaxNo
  - 收款人(payee)/复核(reviewer)/开票人(drawer)
  - `List<InvoiceOcrItem> items`（明细，阶段一暂不填充，预留结构）
  - `String fullText`（OCR 全文，排查用）
  - `List<OcrLine> lines`（原始文本行+坐标+置信度，即 raw）

- **`InvoiceOcrItem.java`** — 明细行 DTO（名称/规格/数量/单价/金额/税率/税额），阶段一预留不填。
- **`OcrLine.java`** — 原始 OCR 行：text/confidence/box(4 点坐标的 `double[8]` 或 4 个 Point)。

### 2. OCR 引擎封装（`srv/ocr/invoice/`）
- **`InvoiceOcrEngine.java`** — `@Component @Slf4j`，封装 RapidOCR4j：
  - 持有单例 `RapidOCR` 实例（`@PostConstruct` 用 `RapidOCR.create()` 初始化，默认加载 jar 内置 PP-OCRv4 模型；`@PreDestroy` 释放）。
  - `List<OcrLine> recognize(byte[] imageBytes)`：调 `ocr.run(byte[])` → `OcrResult.getRecRes()` → 转 `OcrLine` 列表（取 text/confidence/dtBoxes 的 4 点）。
  - **同步非线程安全**（RapidOCR 实例约束）：内部用 `synchronized` 或单线程锁保护 `run` 调用。
  - `healthCheck()`：返回 RapidOCR 实例是否已初始化。

### 3. 字段抽取（`srv/ocr/invoice/`）
- **`InvoiceFieldExtractor.java`** — `@Component`，把 `List<OcrLine>` 抽取为 `InvoiceOcrResult`：
  - **关键字定位**：遍历行文本，命中"发票代码"/"发票号码"/"开票日期"/"价税合计"/"名称"/"纳税人识别号"/"收款人"/"复核"/"开票人"等关键字。
  - **值提取**：关键字同行右侧、或下一行取值（坐标邻接判断 + 正则清洗数字金额）。
  - **购销方区分**：按"名称:"/"纳税人识别号:"前缀 + 上下区段（购方在上、销方在中下）区分。
  - **金额/税额**：正则匹配 `¥?\d+([,.]\d+)+`，结合"价税合计"/"税额"关键字定位。
  - `fullText` 和 `lines` 原样回填，便于排查。
  - 阶段一覆盖：代码、号码、开票日期、金额、税额、价税合计、购销方名称与税号、收款人/复核/开票人。明细行预留接口不实现。

### 4. Service（`srv/ocr/invoice/service/`）
- **`InvoiceOcrService.java`** — `@Service @Slf4j`，编排：
  - `InvoiceOcrResult recognize(byte[] imageBytes)`：`engine.recognize(...)` → `extractor.extract(...)` → 返回。
  - `InvoiceOcrResult recognizeByFileId(String fileId)`：`fileHandler.toFile(fileId)`（兼容本地/OSS）→ 读 `byte[]` → 调上面方法。
  - 注入 `InvoiceOcrEngine`、`InvoiceFieldExtractor`、`FileHandler`。

### 5. Controller（`srv/ocr/invoice/`）
- **`InvoiceOcrController.java`** — `@ApiRestController("/invoice/ocr") @Slf4j extends BaseController`：
  ```java
  // multipart 直传
  @PostMapping("/recognize")
  ApiResult<InvoiceOcrResult> recognize(@RequestParam("file") MultipartFile file) throws IOException {
      if (file == null || file.isEmpty()) return ApiResult.fail("文件不能为空");
      return ApiResult.success(invoiceOcrService.recognize(file.getBytes()));
  }

  // fileId 方式
  @PostMapping("/recognize/fileId")
  ApiResult<InvoiceOcrResult> recognizeByFileId(@RequestBody Map<String,Object> body) {
      String fileId = (String) body.get("fileId");
      if (StringUtils.isBlank(fileId)) return ApiResult.fail("fileId 不能为空");
      return ApiResult.success(invoiceOcrService.recognizeByFileId(fileId));
  }
  ```
  路由：`/invoice/ocr/recognize`、`/invoice/ocr/recognize/fileId`（项目无 `/api` 前缀）。
  超大文件/非图片类型做基本校验。

### 6. 配置项（追加 `geelato-web-quickstart/.../application.properties`）
```properties
# 发票 OCR（阶段一，宿主直接实现）
geelato.ocr.invoice.enabled=${GEELATO_OCR_INVOICE_ENABLED:true}
# 可选：外置 opencv native 路径（loadShared 失败时用，默认空走库内置）
geelato.ocr.invoice.opencv-lib-path=${GEELATO_OCR_OPENCV_LIB_PATH:}
```

---

## 三、关键实现注意点

1. **RapidOCR 线程安全**：实例非线程安全，`InvoiceOcrEngine.recognize` 用 `synchronized` 保护（阶段一并发要求低，简单加锁即可；后续可改对象池）。
2. **模型加载**：默认走 jar 内置（`RapidOCR.create()` 无参），fat jar 下 `getResourceAsStream` 正常，无需手动下模型。
3. **OpenCV native 风险**：默认 `loadShared` 在 Spring Boot 通常顺畅；若失败，用 `geelato.ocr.invoice.opencv-lib-path` 指向外置 dll（`OcrConfig.Global.opencvLibPath`）。
4. **fileId 路径**：`FileHandler.toFile` 在 OSS 场景返回临时文件，读 byte[] 后无需特殊清理（沿用 OCRController 范式）。
5. **字段抽取容错**：任一字段抽不到不抛异常，对应字段留空（null），不影响整体返回——发票版式多样，阶段一求"能抽出多少抽多少"。

---

## 四、验证步骤
1. 编译：`mvn -pl geelato-web-platform compile` + `mvn -pl geelato-web-quickstart compile`。
2. 启动应用（首次会加载 onnxruntime/opencv native + 三个模型，启动略慢属正常）。
3. `curl -F file=@invoice.jpg http://host/invoice/ocr/recognize`，确认返回 `InvoiceOcrResult`（结构化字段 + fullText + lines）。
4. 用真实增值税发票图片测试识别准确率，据此决定字段抽取规则的调优方向。
5. 验证 `/invoice/ocr/recognize/fileId`（先调 `/upload/file` 拿 fileId）。

---

## 五、阶段一不做的事（明确边界）
- ❌ 不做插件化（`InvoiceOCRService` 扩展点、插件 jar、租户门控）——阶段二再做。
- ❌ 不做明细行抽取（预留 `items` 结构，规则后续迭代）。
- ❌ 不做 GPU 推理（CPU 即可验证）。
- ❌ 不做多发票类型（先聚焦增值税专票/普票/电子发票）。

---

## 六、阶段二衔接（验证通过后）
若识别价值得到验证，阶段二把 `InvoiceOcrEngine` + `InvoiceFieldExtractor` 迁入插件工程 `geelato-plugin-invoice-ocr`，定义 `InvoiceOCRService` 扩展点，onnxruntime native 放宿主共享，通过 `/pm/tenant/switch` 分租户开关。阶段一的 DTO/字段抽取/Controller 逻辑大部分可直接复用。

**请审阅。确认后我按此实施。**