# 分页 size 参数完全钳制到 [1, 1000],不再报错

## 现状

校验在 `geelato-web-platform\src\main\java\cn\geelato\web\platform\srv\ParameterOperator.java:182-191` 的 `requirePageSize`:pageSize/limit 小于 1 或大于 `MAX_PAGE_SIZE`(1000)均抛 `InvalidPageParamException`(HTTP 400)。GET 与 POST 两条链路都经过此方法,是全仓库唯一的分页大小上限校验。

## 修改内容

### 1. ParameterOperator.java(主改动)

- `requirePageSize` 数值越界分支全部改为钳制,方法收敛为:`return Math.max(1, Math.min(parseInt(pageSize), MAX_PAGE_SIZE))`
  - `value < 1`(0、负数)→ 强制为 1
  - `value > 1000` → 强制为 1000
  - 恰好在 [1, 1000] 内 → 原样通过
- 行为完全变为钳制后,私有方法 `requirePageSize` 改名为 `clampPageSize`(私有方法,无外部调用方影响);`parseInt` 内非整数(如 "ten")的硬失败保持不变。
- 更新第 29 行常量注释:由"超出直接拒绝(硬失败)"改为"单页大小上限,越界(含 <1)一律钳制到 [1, 上限],不报错"。
- 加一行日志(按同模块 log4j2 惯例):钳制发生时记录参数名、原值与钳制后值,便于排查"传 5000 只返回 1000 条"这类疑问;不对外抛错。

### 2. ParameterOperatorTest.java(测试同步)

- `bodyPageSizeOverLimitFailsHard`(第 100-108 行)→ 改名 `bodyPageSizeOverLimitClampedToMax`:传 pageSize=5000,断言 `getPageSize() == 1000` 且不抛异常。
- `bodyNegativePageSizeFailsHard`(第 93-98 行)→ 改名 `bodyNonPositivePageSizeClampedToOne`:分别传 0 与 -5,断言 `getPageSize() == 1`。
- 补边界用例:pageSize=1000 原样通过;GET 链路 pageSize=1001 钳为 1000。
- 类顶部注释同步:非法值行为改为"非整数硬失败,数值越界钳制"。

## 不变的部分

- GET 未传分页参数的 -1 全量查询语义(不走钳制方法)。
- pageNum 校验(`requirePageNum` 的 <1 仍硬失败)、别名解析(pageSize/limit)、非整数硬失败,均不动。
- 不加配置开关(必然行为,遵循项目惯例)。

## 验证

增量编译并运行(Windows 下避免 clean 防 IDE 文件锁):

```
mvn -pl geelato-web-platform test -Dtest=ParameterOperatorTest
```

## 影响面

所有继承 BaseController 的控制器(约 77 个)统一生效:分页大小越界不再返回 400,而是按钳制值查询。