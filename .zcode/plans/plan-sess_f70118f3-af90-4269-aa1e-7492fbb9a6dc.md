# 打包前校验：物理表与实体元数据不一致则拒绝打包

## 需求

打包时若发现物理表结构与实体定义不同步（如物理表存在 `enable_status` 但实体定义没有），直接阻止打包并明确报错，不允许产出"脏包"。

## 校验规则

- **判定标准（单向）**：物理表的列（`select *` 结果的列集合）中，存在实体元数据没有的列 → 不一致，拒绝打包。
- **反向不拦截**：实体定义有、物理表没有的列不报错——`select *` 打出的包只含物理列，是实体字段的子集，部署校验能通过；且设计器/虚拟字段场景下反向强校验容易误伤。
- 实体在 `metaManager` 中查不到（如未注册的业务表）→ 跳过校验，保持现有行为（部署侧已有"元数据不存在"的明确报错兜底）。
- 表无数据（查询结果为空）→ 跳过（包里本来就没有该表数据，无脏列可打）。
- **收集全部不一致的表后一次性报错**（而非碰到第一张就停），方便一次性清理。

## 修改内容

### 1. `PackageService` 新增公共方法

```java
/**
 * 打包前校验：返回物理表中存在、但实体定义中没有的列（select * 查询结果即物理列集合）。
 */
public List<String> findUnknownColumns(String metaName, List<Map<String, Object>> metaData)
```

- 取首行 keySet 与实体 `fieldMetas` 列名集合比对，返回多余的物理列（排序去重）；无实体/无数据/无不一致返回空列表。

### 2. v1 `PackageController.packetApp`（117 行附近的循环）

- 循环内对每个 `queryForList` 结果调用 `findUnknownColumns`，不一致的表与列收集到 `LinkedHashMap`；
- 循环结束、`writePackageData` 之前，若非空则抛 `PackageException`，消息格式：
  `打包校验失败：物理表与实体定义不一致，禁止打包。实体 [platform_swf_proc_tran_def] 的物理表中存在实体定义没有的字段：[enable_status]；……请先清理物理表遗留列（ALTER TABLE ... DROP COLUMN）或同步实体定义后再打包。`

### 3. v2 `PackageService.buildAppPackDataV2`（276 行附近的循环）

同样接入：循环内收集，方法返回前统一抛 `PackageException`（消息格式一致）。

### 不改的部分

- **打包过滤方案不再采用**（上一版方案被否）：不做列剔除，改为硬校验拦截。
- 部署侧零改动；合并打包（packetMergeApp / packetMergeV2）不重新读库，不涉及。
- `AppMetaUtils`/`PackageUtils` 外部 jar 不动。

## 验证

- `mvn compile` 编译 `geelato-web-platform`；
- 代码审查确认：v1/v2 两条打包路径都拦截、报错包含全部不一致的表与字段、部署路径无任何改动。