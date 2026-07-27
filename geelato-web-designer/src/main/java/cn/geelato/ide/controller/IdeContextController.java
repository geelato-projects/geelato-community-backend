package cn.geelato.ide.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import cn.geelato.ide.service.IdeContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * AI 上下文包 Controller。
 * <p>
 * 路径前缀 {@code /ide/context}：
 * <ul>
 *   <li>{@code GET /ide/context/entities}        - 实体目录</li>
 *   <li>{@code GET /ide/context/mql-grammar}     - MQL 文法</li>
 *   <li>{@code GET /ide/context/graal-services}  - 桥服务签名表</li>
 *   <li>{@code GET /ide/context/languages}       - 三语言使用指南</li>
 *   <li>{@code GET /ide/context/api-endpoints}   - API 端点目录（SrvExplain）</li>
 *   <li>{@code GET /ide/context/all}             - 一键打包</li>
 * </ul>
 *
 * @author geelato
 */
@DesignTimeApiRestController("/ide/context")
public class IdeContextController {

    @Autowired
    private IdeContextService ideContextService;

    @GetMapping("/entities")
    public ApiResult<?> entities() {
        return ApiResult.success(ideContextService.entities());
    }

    @GetMapping("/mql-grammar")
    public ApiResult<?> mqlGrammar() {
        return ApiResult.success(ideContextService.mqlGrammar());
    }

    @GetMapping("/graal-services")
    public ApiResult<?> graalServices() {
        return ApiResult.success(ideContextService.graalServices());
    }

    @GetMapping("/languages")
    public ApiResult<?> languages() {
        return ApiResult.success(ideContextService.languagesGuide());
    }

    @GetMapping("/api-endpoints")
    public ApiResult<?> apiEndpoints() {
        return ApiResult.success(ideContextService.apiEndpoints());
    }

    @GetMapping("/all")
    public ApiResult<?> all() {
        return ApiResult.success(ideContextService.all());
    }
}
