# Geelato Web Common

通用Web组件库，提供跨Servlet和Reactive环境的通用功能。

## 分页处理组件

### 概述

本模块提供了统一的分页参数处理功能，支持：
- Servlet环境（Spring MVC）
- Reactive环境（Spring WebFlux）
- 从URL查询参数和请求体中提取分页参数
- 自动创建MyBatis Plus Page对象

### 核心组件

#### 1. PageParams
分页参数封装类，包含页码和页面大小。

```java
PageParams params = new PageParams(1, 10);
boolean valid = params.isValid(); // 检查参数有效性
int offset = params.getOffset(); // 获取偏移量
```

#### 2. BaseController
通用控制器基类，提供分页参数处理方法。

##### 使用示例（自动适配环境）

```java
@RestController
public class MyController extends BaseController {
    
    @GetMapping("/list")
    public ResponseEntity<?> list() {
        // 方式1：获取分页参数（自动适配当前环境）
        PageParams params = getPageParams();
        
        // 方式2：直接创建Page对象（自动适配当前环境）
        Page<MyEntity> page = createPage();
        
        // 方式3：带默认值
        PageParams paramsWithDefault = getPageParams(1, 20);
        Page<MyEntity> pageWithDefault = createPage(1, 20);
        
        return ResponseEntity.ok(page);
    }
}
```

##### Reactive环境使用示例

```java
@Component
public class MyHandler extends BaseController {
    
    public Mono<ServerResponse> list(ServerRequest request) {
        // 设置当前请求上下文
        this.serverRequest = request;
        
        // 获取分页参数（自动适配Reactive环境）
        PageParams params = getPageParams();
        
        // 使用分页参数进行查询
        return queryService.findByPage(params)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }
    
    public Mono<ServerResponse> listWithPage(ServerRequest request) {
        // 设置当前请求上下文
        this.serverRequest = request;
        
        // 直接创建Page对象（自动适配Reactive环境）
        Page<MyEntity> page = createPage();
        
        return queryService.findByPage(page)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }
}
```

### 分页参数提取规则

1. **优先级**：URL查询参数 > 请求体参数
2. **URL参数**：`?pageNum=1&pageSize=10`
3. **请求体参数**：
   ```json
   {
     "pageNum": 1,
     "pageSize": 10,
     "other": "data"
   }
   ```

### 依赖要求

- Spring Web（必需）
- Jackson（用于JSON解析）
- Spring WebFlux（Reactive支持，可选）
- MyBatis Plus（用于Page对象创建，可选）

### 扩展使用

如果需要自定义分页参数提取逻辑，可以实现`PageParamsExtractor`接口：

```java
public class CustomPageParamsExtractor implements PageParamsExtractor<HttpServletRequest> {
    @Override
    public PageParams extractPageParams(HttpServletRequest request) {
        // 自定义提取逻辑
        return new PageParams(1, 10);
    }
}
```

### 注意事项

1. 在Servlet环境中，如果请求体已被读取，将无法再次读取
2. Reactive环境中的方法返回`Mono`类型，需要配合响应式编程使用
3. 当MyBatis Plus不可用时，会自动降级使用`SimplePage`对象
4. 建议在具体项目中继承`BaseController`并添加项目特定的功能