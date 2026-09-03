# WebFlux 遗留清理（独立小批次，不影响连接池计划）

## 背景
工程最初评估结论：WebFlux 不适合（阻塞 JDBC + ThreadLocal 链），用户确认清理遗留。排查结果：整套"双模 Servlet/Reactive 实验"是零外部引用的封闭圈，删除无编译影响。

## 清理内容
1. **删文件（6 个，全在 geelato-web-common）**：
   - `src/main/java/cn/geelato/web/common/controller/BaseController.java`（455 行双模基类：ServerRequest 字段、geelato.web.architecture 开关、reactor 分支方法；无业务继承——业务用 web-platform 的 srv.BaseController）
   - `src/main/java/cn/geelato/web/common/controller/extractor/` 整包 4 文件：RequestParamsExtractor.java、ServletRequestParamsExtractor.java、ReactiveRequestParamsExtractor.java（bodyToMono().block() 在真实 WebFlux 下必然抛 IllegalStateException，实现本身不可行）、PageParamsExtractor.java
   - `src/main/java/cn/geelato/web/common/controller/PageParams.java`（仅封闭圈内使用；删前再 grep 复核一次 import 面）
2. **删依赖（2 个 pom）**：
   - geelato-web-common/pom.xml：spring-webflux(optional)（L46-51）+ reactor-core（L104-108）
   - geelato-web-platform/pom.xml：spring-boot-starter-webflux（L264-267）
3. **配置键**：`geelato.web.architecture` 唯一使用处即被删基类，无 properties 启用，无需额外清理
4. **不动**：geelato-mcp 的 spring-ai MCP SDK 传递依赖的 reactor（MCP 协议栈需要，独立构建，非 WebFlux 遗留）

## 验证
1. 删除前复核：grep 全工程 `web.common.controller.BaseController|PageParams` 的 import 面（确认零外部引用）
2. 编译链：`mvn -pl geelato-web-common install` → geelato-web-platform → geelato-web-quickstart（IDE 锁 target 时增量 compile）
3. grep 复核残留：`reactor\.core|reactive\.function|ServerRequest|webflux|Mono<|Flux<` 应零命中
4. quickstart 启动冒烟：仍以 MVC 模式正常启动、任一业务接口正常（删除为纯死代码，无行为变化）
5. 回滚：git 恢复删除文件与 pom 即可