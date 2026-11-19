-- =============================================================================
-- 发票项目基础表 il_invoice_item 初始化数据（种子脚本 v0.2）
-- 与《自动开票集成方案.md》《invoice-tables.sql》配套
--
-- 数据来源：用户提供的 7 张既有票面/费用截图（5 个带税收分类简称的既有发票项目
--           + 1 张费用清单：设备交接单费/电放费/封条费/改单费/THC/文件费）
--
-- ⚠️ 重要警告（执行前必读）：
--   1. 【免税口径】既有发票项目均为免税开票（2026-11-18 用户确认），全部行按
--      tax_rate=NULL + free_tax_mark='1' + vat_special_management='免税' 初始化，
--      组票时免税行税率传空（数电票票面税率栏显示“免税”）。
--      若某项实际按一般计税 6% 开票，将该行改为：
--      tax_rate=0.060000、free_tax_mark='0'、vat_special_management=NULL。
--      免税适用范围以税局备案口径为准。
--   2. 税收分类编码经网络检索确定（acc5 税率表、税务实务文章），**上线前必须在
--      百望开票系统的商品编码库或税局最新《商品和服务税收分类编码表》中逐一核对**——
--      开错编码属发票栏目不全/错误，是不合规发票。
--   3. 2026-05 起多地电子税务局完成编码升级，货代类代理服务费的简称已由
--      “经纪代理服务/现代服务”变更为“生产生活服务”，与既有票面一致；
--      goods_simple_name 仅为展示冗余，实际票面简称以税局按编码自动生成为准。
--   4. 截图原文“*产生生活服务*…”疑为“生产生活服务”笔误、“*生产经营服务*…”
--      非标准简称，脚本已按标准简称归一，原文记录在各行 remark。
--
-- 幂等性：uk_item_code 含 delete_at（NULL），MySQL 唯一索引对 NULL 不去重，
--         故采用 WHERE NOT EXISTS 模式，可重复执行。
--   注意：若已按 v0.1（6%）执行过，重复执行本脚本不会覆盖已有行——请用文末
--         【存量纠正 UPDATE】先改值。
-- id 策略：19 位标准雪花数字串（Twitter 纪元 1288834974657，5+5+12 位布局），
--         与 rn_report 种子同款（其 id 1912750541678055421 解码即此格式）；
--         种子 id 为编写期（2026-11-18）按同算法生成的真实值固化，
--         dc=1/wk=1、seq 取 3001~3011 高段，运行期生成器不会重复产出。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 一、既有发票项目（票面出现过的 5 项，免税）
-- -----------------------------------------------------------------------------
INSERT INTO `il_invoice_item`
  (`id`,`item_code`,`item_name`,`goods_simple_name`,`tax_classification_code`,`tax_rate`,
   `specification`,`unit`,`preferential_mark`,`free_tax_mark`,`vat_special_management`,
   `deductible_amount`,`remark`,`seq_no`,`enable_status`,
   `dept_id`,`bu_id`,`tenant_code`,`del_status`,`update_at`,`updater`,`updater_name`,
   `create_at`,`creator`,`creator_name`,`delete_at`)
SELECT '2122905634335759289','FWD_SVC_FEE','国际货物运输代理服务费','生产生活服务','3040405020000000000',NULL,
  NULL,NULL,'0','1','免税',NULL,'既有票面项目，免税开票；原图简称“产生生活服务”疑为“生产生活服务”笔误',1,1,
  NULL,NULL,NULL,0,NOW(),'seed','初始化脚本',NOW(),'seed','初始化脚本',NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `il_invoice_item` WHERE `item_code`='FWD_SVC_FEE' AND `del_status`=0);

INSERT INTO `il_invoice_item`
  (`id`,`item_code`,`item_name`,`goods_simple_name`,`tax_classification_code`,`tax_rate`,
   `specification`,`unit`,`preferential_mark`,`free_tax_mark`,`vat_special_management`,
   `deductible_amount`,`remark`,`seq_no`,`enable_status`,
   `dept_id`,`bu_id`,`tenant_code`,`del_status`,`update_at`,`updater`,`updater_name`,
   `create_at`,`creator`,`creator_name`,`delete_at`)
SELECT '2122905634335759290','FWD_OCEAN_FREIGHT','国际货物运输代理海运费','生产生活服务','3040405020000000000',NULL,
  NULL,NULL,'0','1','免税',NULL,'既有票面项目，免税开票；与“国际货物运输代理服务费”同编码，仅名称区分',2,1,
  NULL,NULL,NULL,0,NOW(),'seed','初始化脚本',NOW(),'seed','初始化脚本',NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `il_invoice_item` WHERE `item_code`='FWD_OCEAN_FREIGHT' AND `del_status`=0);

INSERT INTO `il_invoice_item`
  (`id`,`item_code`,`item_name`,`goods_simple_name`,`tax_classification_code`,`tax_rate`,
   `specification`,`unit`,`preferential_mark`,`free_tax_mark`,`vat_special_management`,
   `deductible_amount`,`remark`,`seq_no`,`enable_status`,
   `dept_id`,`bu_id`,`tenant_code`,`del_status`,`update_at`,`updater`,`updater_name`,
   `create_at`,`creator`,`creator_name`,`delete_at`)
SELECT '2122905634335759291','FWD_CUSTOMS_FEE','代理报关费','经纪代理服务','3040802020000000000',NULL,
  NULL,NULL,'0','1','免税',NULL,'既有票面项目（原图简称“生活服务”），免税开票；304080202=代理报关服务（现代服务-商务辅助-经纪代理）——该项是否适用免税请与税局备案口径核对，若按 6% 开票改三件套',3,1,
  NULL,NULL,NULL,0,NOW(),'seed','初始化脚本',NOW(),'seed','初始化脚本',NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `il_invoice_item` WHERE `item_code`='FWD_CUSTOMS_FEE' AND `del_status`=0);

INSERT INTO `il_invoice_item`
  (`id`,`item_code`,`item_name`,`goods_simple_name`,`tax_classification_code`,`tax_rate`,
   `specification`,`unit`,`preferential_mark`,`free_tax_mark`,`vat_special_management`,
   `deductible_amount`,`remark`,`seq_no`,`enable_status`,
   `dept_id`,`bu_id`,`tenant_code`,`del_status`,`update_at`,`updater`,`updater_name`,
   `create_at`,`creator`,`creator_name`,`delete_at`)
SELECT '2122905634335759292','FWD_TRUCKING_FEE','代理拖车费','生产生活服务','3040405020000000000',NULL,
  NULL,NULL,'0','1','免税',NULL,'既有票面项目（原图简称“生产生活服务”），免税开票；若按交通运输（陆路货运9%）或代理6%开票须换编码并改三件套，需财务确认',4,1,
  NULL,NULL,NULL,0,NOW(),'seed','初始化脚本',NOW(),'seed','初始化脚本',NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `il_invoice_item` WHERE `item_code`='FWD_TRUCKING_FEE' AND `del_status`=0);

INSERT INTO `il_invoice_item`
  (`id`,`item_code`,`item_name`,`goods_simple_name`,`tax_classification_code`,`tax_rate`,
   `specification`,`unit`,`preferential_mark`,`free_tax_mark`,`vat_special_management`,
   `deductible_amount`,`remark`,`seq_no`,`enable_status`,
   `dept_id`,`bu_id`,`tenant_code`,`del_status`,`update_at`,`updater`,`updater_name`,
   `create_at`,`creator`,`creator_name`,`delete_at`)
SELECT '2122905634335759293','FWD_MISC_FEE','代理杂费','经纪代理服务','3040802000000000000',NULL,
  NULL,NULL,'0','1','免税',NULL,'既有票面项目（原图简称“生产经营服务”为非标准简称，疑旧版/笔误），免税开票；归入经纪代理服务父类，具体子目须财务在编码库核定',5,1,
  NULL,NULL,NULL,0,NOW(),'seed','初始化脚本',NOW(),'seed','初始化脚本',NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `il_invoice_item` WHERE `item_code`='FWD_MISC_FEE' AND `del_status`=0);

-- -----------------------------------------------------------------------------
-- 二、费用清单项目（截图费用表：设备交接单费/电放费/封条费/改单费/THC/文件费）
--     免税口径同上，编码均须财务核定后再启用
-- -----------------------------------------------------------------------------
INSERT INTO `il_invoice_item`
  (`id`,`item_code`,`item_name`,`goods_simple_name`,`tax_classification_code`,`tax_rate`,
   `specification`,`unit`,`preferential_mark`,`free_tax_mark`,`vat_special_management`,
   `deductible_amount`,`remark`,`seq_no`,`enable_status`,
   `dept_id`,`bu_id`,`tenant_code`,`del_status`,`update_at`,`updater`,`updater_name`,
   `create_at`,`creator`,`creator_name`,`delete_at`)
SELECT '2122905634335759294','PORT_THC','THC（码头操作费）','生产生活服务','3040401000000000000',NULL,
  NULL,NULL,'0','1','免税',NULL,'费用清单项；THC=港口码头服务（物流辅助服务），编码与免税口径须财务核定',6,1,
  NULL,NULL,NULL,0,NOW(),'seed','初始化脚本',NOW(),'seed','初始化脚本',NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `il_invoice_item` WHERE `item_code`='PORT_THC' AND `del_status`=0);

INSERT INTO `il_invoice_item`
  (`id`,`item_code`,`item_name`,`goods_simple_name`,`tax_classification_code`,`tax_rate`,
   `specification`,`unit`,`preferential_mark`,`free_tax_mark`,`vat_special_management`,
   `deductible_amount`,`remark`,`seq_no`,`enable_status`,
   `dept_id`,`bu_id`,`tenant_code`,`del_status`,`update_at`,`updater`,`updater_name`,
   `create_at`,`creator`,`creator_name`,`delete_at`)
SELECT '2122905634335759295','PORT_EIR_FEE','设备交接单费','生产生活服务','3040401000000000000',NULL,
  NULL,NULL,'0','1','免税',NULL,'费用清单项；暂归港口码头服务，编码与免税口径须财务核定',7,1,
  NULL,NULL,NULL,0,NOW(),'seed','初始化脚本',NOW(),'seed','初始化脚本',NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `il_invoice_item` WHERE `item_code`='PORT_EIR_FEE' AND `del_status`=0);

INSERT INTO `il_invoice_item`
  (`id`,`item_code`,`item_name`,`goods_simple_name`,`tax_classification_code`,`tax_rate`,
   `specification`,`unit`,`preferential_mark`,`free_tax_mark`,`vat_special_management`,
   `deductible_amount`,`remark`,`seq_no`,`enable_status`,
   `dept_id`,`bu_id`,`tenant_code`,`del_status`,`update_at`,`updater`,`updater_name`,
   `create_at`,`creator`,`creator_name`,`delete_at`)
SELECT '2122905634335759296','TLX_RELEASE_FEE','电放费','经纪代理服务','3040802000000000000',NULL,
  NULL,NULL,'0','1','免税',NULL,'费用清单项；船公司附加费代收代付，实务多归经纪代理服务，编码与免税口径须财务核定',8,1,
  NULL,NULL,NULL,0,NOW(),'seed','初始化脚本',NOW(),'seed','初始化脚本',NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `il_invoice_item` WHERE `item_code`='TLX_RELEASE_FEE' AND `del_status`=0);

INSERT INTO `il_invoice_item`
  (`id`,`item_code`,`item_name`,`goods_simple_name`,`tax_classification_code`,`tax_rate`,
   `specification`,`unit`,`preferential_mark`,`free_tax_mark`,`vat_special_management`,
   `deductible_amount`,`remark`,`seq_no`,`enable_status`,
   `dept_id`,`bu_id`,`tenant_code`,`del_status`,`update_at`,`updater`,`updater_name`,
   `create_at`,`creator`,`creator_name`,`delete_at`)
SELECT '2122905634335759297','SEAL_FEE','封条费','生产生活服务','3040401000000000000',NULL,
  NULL,NULL,'0','1','免税',NULL,'费用清单项；暂归港口码头服务，编码与免税口径须财务核定',9,1,
  NULL,NULL,NULL,0,NOW(),'seed','初始化脚本',NOW(),'seed','初始化脚本',NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `il_invoice_item` WHERE `item_code`='SEAL_FEE' AND `del_status`=0);

INSERT INTO `il_invoice_item`
  (`id`,`item_code`,`item_name`,`goods_simple_name`,`tax_classification_code`,`tax_rate`,
   `specification`,`unit`,`preferential_mark`,`free_tax_mark`,`vat_special_management`,
   `deductible_amount`,`remark`,`seq_no`,`enable_status`,
   `dept_id`,`bu_id`,`tenant_code`,`del_status`,`update_at`,`updater`,`updater_name`,
   `create_at`,`creator`,`creator_name`,`delete_at`)
SELECT '2122905634335759298','AMEND_FEE','改单费','经纪代理服务','3040802000000000000',NULL,
  NULL,NULL,'0','1','免税',NULL,'费用清单项；暂归经纪代理服务，编码与免税口径须财务核定',10,1,
  NULL,NULL,NULL,0,NOW(),'seed','初始化脚本',NOW(),'seed','初始化脚本',NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `il_invoice_item` WHERE `item_code`='AMEND_FEE' AND `del_status`=0);

INSERT INTO `il_invoice_item`
  (`id`,`item_code`,`item_name`,`goods_simple_name`,`tax_classification_code`,`tax_rate`,
   `specification`,`unit`,`preferential_mark`,`free_tax_mark`,`vat_special_management`,
   `deductible_amount`,`remark`,`seq_no`,`enable_status`,
   `dept_id`,`bu_id`,`tenant_code`,`del_status`,`update_at`,`updater`,`updater_name`,
   `create_at`,`creator`,`creator_name`,`delete_at`)
SELECT '2122905634335759299','DOC_FEE','文件费','经纪代理服务','3040802000000000000',NULL,
  NULL,NULL,'0','1','免税',NULL,'费用清单项；单证费，暂归经纪代理服务，编码与免税口径须财务核定',11,1,
  NULL,NULL,NULL,0,NOW(),'seed','初始化脚本',NOW(),'seed','初始化脚本',NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `il_invoice_item` WHERE `item_code`='DOC_FEE' AND `del_status`=0);

-- -----------------------------------------------------------------------------
-- 【存量纠正 UPDATE】仅当此前执行过 v0.1（按 6% 初始化）时使用：
--   将全部行改为免税三件套；个别确需 6% 的项目再单独改回。
-- -----------------------------------------------------------------------------
-- UPDATE `il_invoice_item`
--    SET `tax_rate` = NULL,
--        `free_tax_mark` = '1',
--        `vat_special_management` = '免税',
--        `update_at` = NOW(),
--        `updater` = 'seed'
--  WHERE `del_status` = 0
--    AND `tax_rate` IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 执行后验证（预期 11 行，tax_rate 全空、free_tax_mark=1）
-- -----------------------------------------------------------------------------
-- SELECT item_code, item_name, goods_simple_name, tax_classification_code,
--        tax_rate, free_tax_mark, vat_special_management, enable_status
--   FROM il_invoice_item WHERE del_status = 0 ORDER BY seq_no;
