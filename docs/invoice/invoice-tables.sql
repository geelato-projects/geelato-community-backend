-- =============================================================================
-- 自动开票集成 建表 DDL（暂定稿 v0.2，与《自动开票集成方案.md》配套）
--
-- 数据层级：开票申请 il_invoice_req（已有）
--             └─ 1:N 发票主体 il_invoice_subject（新增，一次申请可开多张，每张独立抬头）
--                   └─ 1:N 发票项目明细 il_invoice_item_detail（新增，每张多项目）
--           开票任务 il_invoice_task（新增）与发票主体 1:1，biz 引用主体
--
-- 目标库  ：geelato（与 il_invoice_req 等 il_ 业务表同库，MySQL 8.0+）
-- 执行方式：手工执行（geelato-dyn 无自动建表机制）；新表可重复执行（IF NOT EXISTS）
-- 同源维护：geelato-dyn/geelato-invoice-integrate 模块 src/main/resources/sql/init.sql 与本文件
--           保持一致，修改须两边同步，勿各自漂移
-- 修正 F1 ：il_invoice_task 增 currency 列（组票币种快照，百望数电票仅 CNY）
-- 风格约定：与 il_ 存量表一致 —— utf8mb3、平台标准审计 12 列（id + 11 列）、
--           唯一索引带 del_status/delete_at、ROW_FORMAT=Dynamic
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 发票项目基础表：发票项目与税率的维护源，申请时下拉选择
--    （被选择时字段全量冗余到明细行，不构成引用关系）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `il_invoice_item`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `item_code` varchar(64) NOT NULL COMMENT '项目编码',
  `item_name` varchar(128) NOT NULL COMMENT '票面明细名称；票面显示为“*编码简称*明细名称”，简称由税局按税收分类编码自动生成，此处只存明细名称',
  `goods_simple_name` varchar(64) NULL DEFAULT NULL COMMENT '商品简称；按税收分类编码带出，展示用，不参与开票请求',
  `tax_classification_code` varchar(19) NOT NULL COMMENT '商品和服务税收分类编码（19位数字）；不选编码开具的发票属栏目不全、不合规',
  `tax_rate` decimal(8, 6) UNSIGNED NULL DEFAULT NULL COMMENT '税率，如 0.060000；零税率行（free_tax_mark 非0）不填税率',
  `specification` varchar(64) NULL DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(32) NULL DEFAULT NULL COMMENT '单位',
  `preferential_mark` varchar(2) NULL DEFAULT '0' COMMENT '优惠政策标识：0-不使用、1-使用（使用时 vat_special_management 必填）',
  `free_tax_mark` varchar(2) NULL DEFAULT '0' COMMENT '零税率标识（官方枚举）：0/空-正常征税、1-免税、2-不征税、3-普通零税率；非正常时 tax_rate 须留空且 vat_special_management 必填',
  `vat_special_management` varchar(128) NULL DEFAULT NULL COMMENT '增值税特殊管理（官方枚举：免税/不征税/简易征收/稀土产品/先征后退/100%先征后退/50%先征后退/按3%简易征收等）',
  `deductible_amount` decimal(18, 2) UNSIGNED NULL DEFAULT NULL COMMENT '差额征税扣除额（预留；一期出现非空且>0 的项目组票校验失败，不开票）',
  `remark` varchar(256) NULL DEFAULT NULL COMMENT '备注',
  `seq_no` bigint UNSIGNED NULL DEFAULT 0 COMMENT '排序号，下拉选择时的显示顺序',
  `enable_status` tinyint UNSIGNED NULL DEFAULT 1 COMMENT '启用状态，1：启用、0：禁用',
  `dept_id` varchar(32) NULL DEFAULT NULL COMMENT '部门信息',
  `bu_id` varchar(32) NULL DEFAULT NULL COMMENT '企业信息',
  `tenant_code` varchar(64) NULL DEFAULT NULL COMMENT '租户编码',
  `del_status` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL COMMENT '更新时间',
  `updater` varchar(32) NOT NULL COMMENT '更新者',
  `updater_name` varchar(64) NULL DEFAULT NULL COMMENT '更新人员',
  `create_at` datetime NOT NULL COMMENT '创建时间',
  `creator` varchar(32) NOT NULL COMMENT '创建者',
  `creator_name` varchar(64) NULL DEFAULT NULL COMMENT '创建人员',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_item_code`(`item_code` ASC, `del_status` ASC, `delete_at` ASC) USING BTREE,
  INDEX `idx_enable_seq`(`enable_status` ASC, `seq_no` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '发票项目基础' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 2. 发票主体表：一次申请可开多张发票，每张一个主体（独立发票抬头+项目集合）
--    （只关联开票申请 invoice_req_id，不做其他关联；申请时人工创建：抬头默认从申请
--     带下、可编辑；开票任务按主体逐张开，与主体 1:1；开票成功后由任务回填实际
--     发票信息——发票号码/开票时间/服务端实际金额/发票附件：主体是发票信息的
--     落身之处，任务表保留开票过程记录）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `il_invoice_subject`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `invoice_req_id` varchar(32) NOT NULL COMMENT '开票申请ID（il_invoice_req.id），唯一关联',
  `subject_no` varchar(64) NOT NULL COMMENT '发票主体编号（申请内唯一，建议 申请号-张号，如 REQ202611180001-01）',
  `seq_no` bigint UNSIGNED NULL DEFAULT 0 COMMENT '排序号，申请内的张次顺序',
  `buyer_name` varchar(64) NOT NULL COMMENT '发票抬头-购买方名称（默认从申请带下，可编辑）',
  `buyer_tax_no` varchar(64) NULL DEFAULT NULL COMMENT '购买方纳税人识别号（专票必填，组票校验）',
  `buyer_address` varchar(256) NULL DEFAULT NULL COMMENT '购买方地址（专票票面栏位，默认从申请带下，可编辑）',
  `buyer_bank` varchar(128) NULL DEFAULT NULL COMMENT '购买方开户行（专票票面栏位，默认从申请带下，可编辑）',
  `buyer_bank_account` varchar(64) NULL DEFAULT NULL COMMENT '购买方开户账号',
  `natural_mark` varchar(2) NULL DEFAULT '0' COMMENT '购买方自然人标志：0-单位、1-自然人（仅普票可用，票面抬头自动追加“（个人）”）',
  `invoice_type` varchar(1024) NULL DEFAULT NULL COMMENT '发票类型（默认从申请带下，可编辑；组票时经 cfg 映射白名单转 invoiceTypeCode）',
  `remarks` varchar(512) NULL DEFAULT NULL COMMENT '票面备注（该张）',
  `total_amount` decimal(18, 2) UNSIGNED NOT NULL DEFAULT 0.00 COMMENT '该张价税合计（=项目明细合计；校验：各主体合计之和=申请 total_expenses）',
  `invoice_no` varchar(32) NULL DEFAULT NULL COMMENT '发票号码（开票成功后回填；数电票为 20 位号码，单独唯一定位一张票）',
  `digit_invoice_no` varchar(32) NULL DEFAULT NULL COMMENT '数电号码（开票成功后回填，与发票号码一致；红冲/交付接口以数电号码为键）',
  `invoice_time` datetime NULL DEFAULT NULL COMMENT '开票时间（开票成功后回填，平台返回）',
  `total_price` decimal(18, 2) UNSIGNED NULL DEFAULT NULL COMMENT '不含税合计（开票成功后按平台服务端重算值回填）',
  `total_tax` decimal(18, 2) UNSIGNED NULL DEFAULT NULL COMMENT '税额合计（开票成功后按平台服务端重算值回填；total_amount 保留申请口径作对账）',
  `file_id` varchar(32) NULL DEFAULT NULL COMMENT '发票文件转存附件ID（PDF，文件归档成功后回填，申请单上直接查看）',
  `dept_id` varchar(32) NULL DEFAULT NULL COMMENT '部门信息',
  `bu_id` varchar(32) NULL DEFAULT NULL COMMENT '企业信息',
  `tenant_code` varchar(64) NULL DEFAULT NULL COMMENT '租户编码',
  `del_status` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL COMMENT '更新时间',
  `updater` varchar(32) NOT NULL COMMENT '更新者',
  `updater_name` varchar(64) NULL DEFAULT NULL COMMENT '更新人员',
  `create_at` datetime NOT NULL COMMENT '创建时间',
  `creator` varchar(32) NOT NULL COMMENT '创建者',
  `creator_name` varchar(64) NULL DEFAULT NULL COMMENT '创建人员',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_subject_no`(`subject_no` ASC, `del_status` ASC, `delete_at` ASC) USING BTREE,
  INDEX `idx_invoice_req_id`(`invoice_req_id` ASC, `del_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '发票主体' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 3. 发票项目明细表：发票主体（一张发票）下的项目行，申请时人工填写，是组票的输入数据
--    （选择基础表项目时字段全量冗余进本表，不保留基础表引用——基础表后续修改/
--     删除不影响已申请数据，item_code 即业务追溯键；
--     挂发票主体 invoice_subject_id；对开票任务透明——任务只读，不回写；
--     行级服务端回算值保留在任务表 request_payload/last_response 报文快照中）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `il_invoice_item_detail`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `invoice_subject_id` varchar(32) NOT NULL COMMENT '发票主体ID（il_invoice_subject.id）',
  `line_no` int NULL DEFAULT NULL COMMENT '行号',
  `item_code` varchar(64) NULL DEFAULT NULL COMMENT '项目编码（选择基础表项目时冗余，业务追溯键）',
  `goods_name` varchar(128) NOT NULL COMMENT '票面项目名称（选择时快照；简称由税局按编码自动生成）',
  `goods_simple_name` varchar(64) NULL DEFAULT NULL COMMENT '商品简称（选择时按税收分类编码带出，展示用）',
  `tax_classification_code` varchar(19) NOT NULL COMMENT '税收分类编码（选择时快照，19位数字）',
  `tax_rate` decimal(8, 6) UNSIGNED NULL DEFAULT NULL COMMENT '税率（选择时快照；零税率行留空）',
  `specification` varchar(64) NULL DEFAULT NULL COMMENT '规格型号（快照）',
  `unit` varchar(32) NULL DEFAULT NULL COMMENT '单位（快照）',
  `preferential_mark` varchar(2) NULL DEFAULT '0' COMMENT '优惠政策标识（快照，0-不使用/1-使用）',
  `free_tax_mark` varchar(2) NULL DEFAULT '0' COMMENT '零税率标识（快照：0/空-正常、1-免税、2-不征税、3-普通零税率）',
  `vat_special_management` varchar(128) NULL DEFAULT NULL COMMENT '增值税特殊管理（快照，零税率行必填）',
  `deductible_amount` decimal(18, 2) UNSIGNED NULL DEFAULT NULL COMMENT '差额征税扣除额（快照，预留；一期非空且>0 组票校验失败）',
  `invoice_line_nature` varchar(1) NULL DEFAULT '0' COMMENT '发票行性质：0-普通行、1-折扣行（预留；一期非0组票校验失败）',
  `quantity` decimal(18, 6) UNSIGNED NULL DEFAULT NULL COMMENT '数量；空则按 1 组票',
  `price` decimal(18, 6) UNSIGNED NULL DEFAULT NULL COMMENT '含税单价（priceTaxMark=1）；空则等于 amount',
  `amount` decimal(18, 2) UNSIGNED NOT NULL COMMENT '该项目含税金额（人工填写；校验行金额>0、合计=所属主体 total_amount）',
  `remark` varchar(256) NULL DEFAULT NULL COMMENT '备注（可注明对应费用范围，不与费用明细强绑定）',
  `dept_id` varchar(32) NULL DEFAULT NULL COMMENT '部门信息',
  `bu_id` varchar(32) NULL DEFAULT NULL COMMENT '企业信息',
  `tenant_code` varchar(64) NULL DEFAULT NULL COMMENT '租户编码',
  `del_status` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL COMMENT '更新时间',
  `updater` varchar(32) NOT NULL COMMENT '更新者',
  `updater_name` varchar(64) NULL DEFAULT NULL COMMENT '更新人员',
  `create_at` datetime NOT NULL COMMENT '创建时间',
  `creator` varchar(32) NOT NULL COMMENT '创建者',
  `creator_name` varchar(64) NULL DEFAULT NULL COMMENT '创建人员',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_invoice_subject_id`(`invoice_subject_id` ASC, `del_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '发票项目明细' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 4. 开票任务表：来源无关（biz_*）、渠道无关（channel）的开票记录总表
--    （biz 引用发票主体：一次申请多张发票=多个任务，每主体一任务、1:1）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `il_invoice_task`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `task_no` varchar(64) NOT NULL COMMENT '任务编号（业务号，Snowflake 生成；order_no 的来源）',
  `biz_type` varchar(32) NOT NULL DEFAULT 'INVOICE_SUBJECT' COMMENT '业务来源类型：INVOICE_SUBJECT=发票主体（主体再溯源开票申请）；后续其他业务来源扩展',
  `biz_id` varchar(32) NOT NULL COMMENT '来源单据ID（INVOICE_SUBJECT 时为 il_invoice_subject.id）',
  `biz_no` varchar(64) NULL DEFAULT NULL COMMENT '来源单据号（冗余展示，建议 申请号+张号，如 REQ202611180001-01）',
  `task_type` varchar(2) NULL DEFAULT '1' COMMENT '任务类型：1-蓝票开具、2-红冲（二期预留）',
  `channel` varchar(16) NULL DEFAULT 'BW' COMMENT '开票平台（取开票信息 invoicing_platform）：BW-百望、NN-诺诺（预留）',
  `task_status` varchar(2) NOT NULL DEFAULT '00' COMMENT '任务状态：00-待提交、01-开票中、02-开票成功、03-开票失败(平台侧)、04-组票校验失败(未提交)、06-已冲红(预留)、07-已关闭(解除对来源单据占用)',
  `seller_invoicing_id` varchar(32) NULL DEFAULT NULL COMMENT '开票信息ID（il_info_for_invoicing.id，发票主体/销方，来自申请）',
  `seller_name` varchar(64) NULL DEFAULT NULL COMMENT '销方名称（组票快照，取申请行 seller）',
  `seller_tax_no` varchar(64) NULL DEFAULT NULL COMMENT '销方纳税人识别号（组票快照，取申请行 seller_tax_id，开票必传）',
  `seller_address` varchar(128) NULL DEFAULT NULL COMMENT '销方地址（组票快照，取申请行）',
  `seller_tel` varchar(18) NULL DEFAULT NULL COMMENT '销方电话（组票快照，取申请行）',
  `seller_bank` varchar(128) NULL DEFAULT NULL COMMENT '销方开户行（组票快照，取申请行）',
  `seller_bank_account` varchar(64) NULL DEFAULT NULL COMMENT '销方开户账号（组票快照，取申请行）',
  `buyer_name` varchar(64) NULL DEFAULT NULL COMMENT '购方名称/发票抬头（组票快照，取自发票主体行）',
  `buyer_tax_no` varchar(64) NULL DEFAULT NULL COMMENT '购方纳税人识别号（组票快照）',
  `buyer_address` varchar(256) NULL DEFAULT NULL COMMENT '购方地址（组票快照）',
  `buyer_tel` varchar(64) NULL DEFAULT NULL COMMENT '购方电话（组票快照，取自申请 buy_tel；主体表不存电话）',
  `buyer_email` varchar(200) NULL DEFAULT NULL COMMENT '购方邮箱（组票快照，取自申请 buyer_email，用于发票交付；主体表不存邮箱）',
  `buyer_bank` varchar(128) NULL DEFAULT NULL COMMENT '购方开户行（组票快照）',
  `buyer_bank_account` varchar(64) NULL DEFAULT NULL COMMENT '购方开户账号（组票快照）',
  `natural_mark` varchar(2) NULL DEFAULT '0' COMMENT '购方自然人标志：0-单位、1-自然人（票面抬头自动追加“（个人）”）',
  `invoice_type_code` varchar(4) NULL DEFAULT '02' COMMENT '票种编码（官方枚举）：01-数电专票、02-数电普票、026-电子普票(文档默认)、028-电子专票、004/007/025-纸质专票/普票/卷式、85/86-数电纸质专票/普票；由主体 invoice_type 经 cfg 映射白名单转换',
  `invoice_special_mark` varchar(3) NULL DEFAULT '00' COMMENT '特定业务标志（官方枚举）：00-普通发票、01-成品油、02-稀土、03-建筑服务、04-货物运输服务、05-不动产销售、09-旅客运输服务等；与票种映射一并由 cfg 配置决定',
  `remarks` varchar(512) NULL DEFAULT NULL COMMENT '票面备注（快照自主体）',
  `price_tax_mark` char(1) NULL DEFAULT '1' COMMENT '单价含税标志：1-单价含税（组票口径快照）',
  `order_no` varchar(64) NULL DEFAULT NULL COMMENT '开票平台订单号（幂等键，全局唯一；=channel 前缀+task_no，如 BW+task_no）',
  `serial_no` varchar(64) NULL DEFAULT NULL COMMENT '平台受理号（开票请求响应返回，结果轮询键）',
  `total_price` decimal(18, 2) UNSIGNED NULL DEFAULT NULL COMMENT '不含税合计（开票成功后按平台服务端重算值回填）',
  `total_tax` decimal(18, 2) UNSIGNED NULL DEFAULT NULL COMMENT '税额合计（开票成功后按平台服务端重算值回填）',
  `total_price_tax` decimal(18, 2) UNSIGNED NULL DEFAULT NULL COMMENT '价税合计（提交前=主体 total_amount，成功后以平台重算值核对回填）',
  `currency` varchar(64) NOT NULL DEFAULT 'CNY' COMMENT '币种快照（F1修正：百望数电票仅CNY，非CNY申请级拒绝）',
  `invoice_no` varchar(32) NULL DEFAULT NULL COMMENT '发票号码（数电票为 20 位号码，单独唯一定位一张票）',
  `digit_invoice_no` varchar(32) NULL DEFAULT NULL COMMENT '数电号码（与发票号码一致；红冲/交付接口以数电号码为键）',
  `invoice_time` datetime NULL DEFAULT NULL COMMENT '开票时间（平台返回）',
  `pdf_url` varchar(512) NULL DEFAULT NULL COMMENT '发票PDF文件地址（平台返回，有时效须及时转存）',
  `ofd_url` varchar(512) NULL DEFAULT NULL COMMENT '发票OFD文件地址（平台返回，有时效须及时转存）',
  `xml_url` varchar(512) NULL DEFAULT NULL COMMENT '发票XML文件地址（数电票法定归档原件，平台返回，有时效须及时转存）',
  `deliver_short_url` varchar(512) NULL DEFAULT NULL COMMENT '购方交付短链（平台返回）',
  `file_id_pdf` varchar(32) NULL DEFAULT NULL COMMENT 'PDF转存平台附件ID',
  `file_id_ofd` varchar(32) NULL DEFAULT NULL COMMENT 'OFD转存平台附件ID',
  `file_id_xml` varchar(32) NULL DEFAULT NULL COMMENT 'XML转存平台附件ID',
  `file_sync_status` varchar(2) NULL DEFAULT '0' COMMENT '发票文件转存状态：0-待转存、1-成功、2-失败待重试（转存失败不阻断开票成功状态）',
  `retry_count` int UNSIGNED NULL DEFAULT 0 COMMENT '重试次数（提交/轮询/转存累计）',
  `error_code` varchar(64) NULL DEFAULT NULL COMMENT '失败错误码（平台 subCode 等）',
  `error_message` varchar(1024) NULL DEFAULT NULL COMMENT '失败原因（组票校验失败时为具体校验项含张号，平台失败时为平台错误信息）',
  `last_poll_at` datetime NULL DEFAULT NULL COMMENT '最后一次结果轮询时间',
  `poll_count` int UNSIGNED NULL DEFAULT 0 COMMENT '结果轮询次数',
  `request_payload` mediumtext NULL COMMENT '提交平台的完整报文快照（诊断/重放）',
  `last_response` mediumtext NULL COMMENT '最近一次平台查询响应快照（诊断）',
  `submit_at` datetime NULL DEFAULT NULL COMMENT '提交平台时间',
  `finish_at` datetime NULL DEFAULT NULL COMMENT '终态时间（成功/失败）',
  `dept_id` varchar(32) NULL DEFAULT NULL COMMENT '部门信息（从来源单据复制）',
  `bu_id` varchar(32) NULL DEFAULT NULL COMMENT '企业信息（从来源单据复制）',
  `tenant_code` varchar(64) NULL DEFAULT NULL COMMENT '租户编码（从来源单据复制）',
  `del_status` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL COMMENT '更新时间',
  `updater` varchar(32) NOT NULL COMMENT '更新者（系统身份 dyn-invoice）',
  `updater_name` varchar(64) NULL DEFAULT NULL COMMENT '更新人员',
  `create_at` datetime NOT NULL COMMENT '创建时间',
  `creator` varchar(32) NOT NULL COMMENT '创建者（系统身份 dyn-invoice）',
  `creator_name` varchar(64) NULL DEFAULT NULL COMMENT '创建人员',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_task_no`(`task_no` ASC, `del_status` ASC, `delete_at` ASC) USING BTREE,
  UNIQUE INDEX `uk_order_no`(`order_no` ASC, `del_status` ASC, `delete_at` ASC) USING BTREE,
  INDEX `idx_biz`(`biz_type` ASC, `biz_id` ASC, `task_status` ASC) USING BTREE,
  INDEX `idx_seller_invoicing_id`(`seller_invoicing_id` ASC) USING BTREE,
  INDEX `idx_task_status`(`task_status` ASC, `del_status` ASC) USING BTREE,
  INDEX `idx_serial_no`(`serial_no` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '开票任务' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 5. 存量表加列（已勘误：无需执行）
--    真实库 il_info_for_invoicing 已存在 invoicing_platform varchar(64)（已核实），
--    dyn 扫描按该列路由；原建议新增 invoice_platform 作废，避免双列漂移。
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- 执行后验证
-- -----------------------------------------------------------------------------
-- SHOW CREATE TABLE il_invoice_subject;
-- SELECT table_name, table_comment FROM information_schema.tables
--  WHERE table_schema = DATABASE() AND table_name IN
--        ('il_invoice_item','il_invoice_subject','il_invoice_item_detail','il_invoice_task');
