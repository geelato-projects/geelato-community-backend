package cn.geelato.srvexplain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithRange;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.javadoc.Javadoc;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class GeneratorMain {
    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of(
            "ApiRestController",
            "ApiRuntimeRestController",
            "RestController",
            "Controller"
    );

    private static final Set<String> MAPPING_ANNOTATIONS = Set.of(
            "RequestMapping",
            "GetMapping",
            "PostMapping",
            "PutMapping",
            "DeleteMapping",
            "PatchMapping",
            "RuntimeMapping"
    );

    private static final Set<String> INFRA_PARAM_TYPES = Set.of(
            "HttpServletRequest",
            "jakarta.servlet.http.HttpServletRequest",
            "HttpServletResponse",
            "jakarta.servlet.http.HttpServletResponse",
            "ServletRequest",
            "jakarta.servlet.ServletRequest",
            "ServletResponse",
            "jakarta.servlet.ServletResponse"
    );

    public static void main(String[] args) throws Exception {
        CliArgs cli = CliArgs.parse(args);
        Path repoRoot = resolveRepoRoot(cli.repoRoot);
        Path outRoot = repoRoot.resolve("SrvExplain");
        Path templatesRoot = outRoot.resolve("_templates");
        Path metaRoot = outRoot.resolve("_meta");

        List<String> modules = readRootModules(repoRoot.resolve("pom.xml"));

        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setCharacterEncoding(StandardCharsets.UTF_8));

        Map<String, List<ControllerDoc>> controllersByModule = new TreeMap<>();
        Map<String, List<EndpointDoc>> endpointIndex = new HashMap<>();

        for (String module : modules) {
            Path moduleSrc = repoRoot.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(moduleSrc)) {
                continue;
            }
            List<Path> javaFiles = listJavaFiles(moduleSrc);
            List<ControllerDoc> docs = new ArrayList<>();
            for (Path javaFile : javaFiles) {
                Optional<ControllerDoc> docOpt = parseController(parser, repoRoot, module, javaFile);
                docOpt.ifPresent(docs::add);
            }

            docs.sort(Comparator.comparing(d -> d.controllerName));
            controllersByModule.put(module, docs);
            for (ControllerDoc c : docs) {
                for (EndpointDoc e : c.endpoints) {
                    String key = (e.httpMethod == null ? "ANY" : e.httpMethod) + " " + e.fullPath;
                    endpointIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
                }
            }
        }

        ensureDir(outRoot);
        ensureDir(metaRoot);

        renderGlobalIndex(outRoot, controllersByModule);
        for (Map.Entry<String, List<ControllerDoc>> e : controllersByModule.entrySet()) {
            renderModuleIndex(outRoot, e.getKey(), e.getValue());
            renderControllers(outRoot, templatesRoot, e.getKey(), e.getValue());
        }
        renderMeta(metaRoot, controllersByModule, endpointIndex);
    }

    private static void renderControllers(Path outRoot, Path templatesRoot, String module, List<ControllerDoc> controllers) {
        if (controllers.isEmpty()) {
            return;
        }
        Path controllersDir = outRoot.resolve(module).resolve("controllers");
        ensureDir(controllersDir);

        String controllerTpl = readTemplateOrEmpty(templatesRoot.resolve("controller.md"));
        String endpointTpl = readTemplateOrEmpty(templatesRoot.resolve("endpoint.md"));

        for (ControllerDoc controller : controllers) {
            String endpointsTable = renderEndpointsTable(controller.endpoints);
            String endpointsDetail = controller.endpoints.stream()
                    .sorted(Comparator.comparing((EndpointDoc x) -> x.fullPath).thenComparing(x -> x.httpMethod == null ? "ANY" : x.httpMethod))
                    .map(ep -> renderEndpointDetail(endpointTpl, ep))
                    .collect(Collectors.joining("\n\n"));

            String content;
            if (!controllerTpl.isBlank()) {
                content = controllerTpl
                        .replace("{{controllerName}}", controller.controllerName)
                        .replace("{{moduleName}}", module)
                        .replace("{{controllerFqn}}", controller.controllerFqn)
                        .replace("{{basePaths}}", String.join(", ", controller.basePaths))
                        .replace("{{conditional}}", controller.conditional == null ? "" : controller.conditional)
                        .replace("{{category}}", controller.category == null ? "" : controller.category)
                        .replace("{{sourceLink}}", controller.sourceLink == null ? "" : controller.sourceLink)
                        .replace("{{endpointsTable}}", endpointsTable)
                        .replace("{{endpointsDetail}}", endpointsDetail);
            } else {
                content = "# " + controller.controllerName + "\n\n"
                        + "- 模块：" + module + "\n"
                        + "- Controller：" + controller.controllerFqn + "\n"
                        + "- BasePath：" + String.join(", ", controller.basePaths) + "\n"
                        + (controller.conditional == null ? "" : "- 条件装配：" + controller.conditional + "\n")
                        + (controller.category == null ? "" : "- 分类：" + controller.category + "\n")
                        + "\n"
                        + "## 接口列表\n\n"
                        + endpointsTable + "\n\n"
                        + "## 接口详情\n\n"
                        + endpointsDetail + "\n";
            }

            writeUtf8(controllersDir.resolve(controller.controllerName + ".md"), content.trim() + "\n");
        }
    }

    private static String renderEndpointDetail(String endpointTpl, EndpointDoc ep) {
        String headersTable = renderParamsTable(ep.headers);
        String pathParamsTable = renderParamsTable(ep.pathParams);
        String queryParamsTable = renderParamsTable(ep.queryParams);

        String bodySpec;
        if (ep.body == null) {
            bodySpec = "无";
        } else {
            bodySpec = "- Java 类型：" + ep.body.javaType + "\n"
                    + (ep.body.uncertain ? "- 推导不确定：是\n" : "");
        }

        String responseSpec = ep.responseJavaType == null ? "" : "- Java 返回类型：" + ep.responseJavaType;

        String curl = ep.curlExample == null ? "" : ep.curlExample;

        if (!endpointTpl.isBlank()) {
            return endpointTpl
                    .replace("{{summary}}", ep.summary == null ? "" : ep.summary)
                    .replace("{{httpMethod}}", ep.httpMethod == null ? "ANY" : ep.httpMethod)
                    .replace("{{fullPath}}", ep.fullPath)
                    .replace("{{produces}}", ep.produces == null ? "" : ep.produces)
                    .replace("{{consumes}}", ep.consumes == null ? "" : ep.consumes)
                    .replace("{{auth}}", ep.auth == null ? "" : ep.auth)
                    .replace("{{methodLink}}", ep.methodLink == null ? "" : ep.methodLink)
                    .replace("{{headersTable}}", headersTable)
                    .replace("{{pathParamsTable}}", pathParamsTable)
                    .replace("{{queryParamsTable}}", queryParamsTable)
                    .replace("{{bodySpec}}", bodySpec)
                    .replace("{{responseSpec}}", responseSpec)
                    .replace("{{curlExample}}", curl);
        }

        return "### " + (ep.summary == null ? "" : ep.summary) + "\n\n"
                + "- Method：" + (ep.httpMethod == null ? "ANY" : ep.httpMethod) + "\n"
                + "- Path：`" + ep.fullPath + "`\n"
                + (ep.produces == null ? "" : "- Produces：" + ep.produces + "\n")
                + (ep.consumes == null ? "" : "- Consumes：" + ep.consumes + "\n")
                + (ep.auth == null ? "" : "- 鉴权：" + ep.auth + "\n")
                + (ep.methodLink == null ? "" : "- 源码：" + ep.methodLink + "\n")
                + "\n"
                + "#### Header\n"
                + headersTable + "\n\n"
                + "#### Path 参数\n"
                + pathParamsTable + "\n\n"
                + "#### Query 参数\n"
                + queryParamsTable + "\n\n"
                + "#### Body\n"
                + bodySpec + "\n\n"
                + "#### Response\n"
                + responseSpec + "\n\n"
                + "#### curl 示例\n"
                + "```bash\n"
                + curl + "\n"
                + "```";
    }

    private static String renderEndpointsTable(List<EndpointDoc> endpoints) {
        if (endpoints.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("| Summary | Method | Path |\n");
        sb.append("|---|---|---|\n");
        endpoints.stream()
                .sorted(Comparator.comparing((EndpointDoc x) -> x.fullPath).thenComparing(x -> x.httpMethod == null ? "ANY" : x.httpMethod))
                .forEach(ep -> sb.append("| ")
                        .append(escapeTable(ep.summary))
                        .append(" | ")
                        .append(ep.httpMethod == null ? "ANY" : ep.httpMethod)
                        .append(" | `")
                        .append(ep.fullPath)
                        .append("` |\n"));
        return sb.toString().trim();
    }

    private static String renderParamsTable(List<ParamDoc> params) {
        if (params == null || params.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("| 名称 | Java 类型 | 必填 | 默认值 | 说明 |\n");
        sb.append("|---|---|---|---|---|\n");
        for (ParamDoc p : params) {
            sb.append("| ")
                    .append(escapeTable(p.name))
                    .append(" | ")
                    .append(escapeTable(p.javaType))
                    .append(" | ")
                    .append(p.required == null ? "" : (p.required ? "是" : "否"))
                    .append(" | ")
                    .append(escapeTable(p.defaultValue))
                    .append(" | ")
                    .append(escapeTable(p.description))
                    .append(" |\n");
        }
        return sb.toString().trim();
    }

    private static String escapeTable(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("|", "\\|").replace("\n", " ").trim();
    }

    private static void renderGlobalIndex(Path outRoot, Map<String, List<ControllerDoc>> controllersByModule) {
        Path index = outRoot.resolve("index.md");
        StringBuilder sb = new StringBuilder();
        sb.append("# 接口索引（全仓库）\n\n");
        sb.append("该文件由生成器自动生成。手工编辑会在下次生成时被覆盖。\n\n");
        sb.append("- 全局说明：见 [README.md](README.md)\n\n");
        sb.append("## 模块列表\n\n");
        for (Map.Entry<String, List<ControllerDoc>> e : controllersByModule.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            sb.append("- [").append(e.getKey()).append("](").append(e.getKey()).append("/README.md)\n");
        }
        sb.append("\n");
        writeUtf8(index, sb.toString());
    }

    private static void renderModuleIndex(Path outRoot, String module, List<ControllerDoc> controllers) {
        if (controllers.isEmpty()) {
            return;
        }
        Path moduleDir = outRoot.resolve(module);
        ensureDir(moduleDir);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(module).append("\n\n");
        sb.append("该文件由生成器自动生成。手工编辑会在下次生成时被覆盖。\n\n");
        sb.append("- 返回全仓库索引：[../index.md](../index.md)\n\n");
        sb.append("## Controllers\n\n");
        for (ControllerDoc c : controllers) {
            sb.append("- [").append(c.controllerName).append("](controllers/").append(c.controllerName).append(".md)");
            if (!c.basePaths.isEmpty()) {
                sb.append("（").append(String.join(", ", c.basePaths)).append("）");
            }
            sb.append("\n");
        }
        sb.append("\n");
        writeUtf8(moduleDir.resolve("README.md"), sb.toString());
    }

    private static void renderMeta(Path metaRoot, Map<String, List<ControllerDoc>> controllersByModule, Map<String, List<EndpointDoc>> endpointIndex) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("generatedAt", OffsetDateTime.now().toString());
        int controllerCount = controllersByModule.values().stream().mapToInt(List::size).sum();
        int endpointCount = controllersByModule.values().stream()
                .flatMap(List::stream)
                .mapToInt(c -> c.endpoints.size())
                .sum();
        manifest.put("controllerCount", controllerCount);
        manifest.put("endpointCount", endpointCount);

        int missingSummary = (int) controllersByModule.values().stream()
                .flatMap(List::stream)
                .flatMap(c -> c.endpoints.stream())
                .filter(e -> e.summary == null || e.summary.isBlank())
                .count();
        manifest.put("missingSummaryEndpointCount", missingSummary);

        int uncertainBodyCount = (int) controllersByModule.values().stream()
                .flatMap(List::stream)
                .flatMap(c -> c.endpoints.stream())
                .filter(e -> e.body != null && e.body.uncertain)
                .count();
        manifest.put("uncertainBodyEndpointCount", uncertainBodyCount);

        writeJson(metaRoot.resolve("manifest.json"), manifest);

        StringBuilder conflicts = new StringBuilder();
        conflicts.append("# conflicts\n\n");
        conflicts.append("该文件由生成器自动生成。\n\n");

        List<Map.Entry<String, List<EndpointDoc>>> duplicates = endpointIndex.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .sorted(Map.Entry.comparingByKey())
                .toList();

        if (duplicates.isEmpty()) {
            conflicts.append("- 未发现重复 method+path\n");
        } else {
            conflicts.append("## 重复 method+path\n\n");
            for (Map.Entry<String, List<EndpointDoc>> dup : duplicates) {
                conflicts.append("### ").append(dup.getKey()).append("\n\n");
                for (EndpointDoc e : dup.getValue()) {
                    conflicts.append("- ").append(e.controllerFqn).append("#").append(e.methodName);
                    if (e.methodLink != null) {
                        conflicts.append("（").append(e.methodLink).append("）");
                    }
                    conflicts.append("\n");
                }
                conflicts.append("\n");
            }
        }

        writeUtf8(metaRoot.resolve("conflicts.md"), conflicts.toString());
    }

    private static String readTemplateOrEmpty(Path path) {
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<ControllerDoc> parseController(JavaParser parser, Path repoRoot, String module, Path javaFile) {
        ParseResult<CompilationUnit> parse;
        try {
            parse = parser.parse(javaFile);
        } catch (IOException e) {
            return Optional.empty();
        }
        if (parse.getResult().isEmpty()) {
            return Optional.empty();
        }
        CompilationUnit cu = parse.getResult().get();
        String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");

        List<ClassOrInterfaceDeclaration> candidates = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(c -> c.isTopLevelType())
                .toList();

        for (ClassOrInterfaceDeclaration c : candidates) {
            if (!hasAnyAnnotationSimpleName(c, CONTROLLER_ANNOTATIONS)) {
                continue;
            }
            if (c.getNameAsString().endsWith("Advice") || c.getNameAsString().endsWith("ExceptionHandler")) {
                continue;
            }

            List<EndpointDoc> endpoints = parseEndpoints(repoRoot, module, javaFile, pkg, c);
            if (endpoints.isEmpty()) {
                continue;
            }

            ControllerDoc doc = new ControllerDoc();
            doc.module = module;
            doc.controllerName = c.getNameAsString();
            doc.controllerFqn = (pkg.isBlank() ? doc.controllerName : pkg + "." + doc.controllerName);
            doc.sourceLink = fileUriWithRange(javaFile, c);

            ControllerAnnotationInfo apiCtl = findApiControllerInfo(c);
            if (apiCtl != null) {
                doc.category = apiCtl.category;
                doc.conditional = apiCtl.conditional;
            }

            List<String> basePaths = new ArrayList<>();
            basePaths.addAll(extractPathsFromAnnotation(c, "ApiRestController"));
            basePaths.addAll(extractPathsFromAnnotation(c, "ApiRuntimeRestController"));
            basePaths.addAll(extractPathsFromAnnotation(c, "RequestMapping"));
            basePaths = normalizePaths(basePaths);
            if (basePaths.isEmpty()) {
                basePaths = List.of("");
            }
            doc.basePaths = basePaths;

            List<EndpointDoc> finalEndpoints = new ArrayList<>();
            for (EndpointDoc ep : endpoints) {
                if (ep.relativePaths.isEmpty()) {
                    for (String bp : basePaths) {
                        EndpointDoc cpy = ep.copy();
                        cpy.fullPath = joinPath(bp, "");
                        cpy.curlExample = buildCurlExample(cpy);
                        finalEndpoints.add(cpy);
                    }
                    continue;
                }
                for (String rp : ep.relativePaths) {
                    for (String bp : basePaths) {
                        EndpointDoc cpy = ep.copy();
                        cpy.fullPath = joinPath(bp, rp);
                        cpy.curlExample = buildCurlExample(cpy);
                        finalEndpoints.add(cpy);
                    }
                }
            }

            for (EndpointDoc ep : finalEndpoints) {
                ep.controllerFqn = doc.controllerFqn;
                ep.controllerName = doc.controllerName;
            }

            doc.endpoints = finalEndpoints;
            return Optional.of(doc);
        }

        return Optional.empty();
    }

    private static List<EndpointDoc> parseEndpoints(Path repoRoot, String module, Path javaFile, String pkg, ClassOrInterfaceDeclaration c) {
        List<EndpointDoc> endpoints = new ArrayList<>();
        for (MethodDeclaration m : c.getMethods()) {
            if (!m.isPublic()) {
                continue;
            }
            AnnotationExpr mapping = findFirstMappingAnnotation(m);
            if (mapping == null) {
                continue;
            }

            EndpointDoc ep = new EndpointDoc();
            ep.module = module;
            ep.methodName = m.getNameAsString();
            ep.methodLink = fileUriWithRange(javaFile, m);

            String controllerFqn = (pkg.isBlank() ? c.getNameAsString() : pkg + "." + c.getNameAsString());
            ep.controllerFqn = controllerFqn;

            MappingInfo mappingInfo = parseMapping(mapping);
            ep.httpMethod = mappingInfo.httpMethod;
            ep.relativePaths = normalizePaths(mappingInfo.paths);
            ep.produces = mappingInfo.produces;
            ep.consumes = mappingInfo.consumes;

            ep.summary = resolveSummary(m).orElse(ep.methodName);

            boolean ignoreVerify = hasAnnotationSimpleName(m, "IgnoreVerify");
            ep.auth = ignoreVerify ? "无需 Authorization（@IgnoreVerify）" : "需要 Authorization";

            List<ParamDoc> headers = new ArrayList<>();
            List<ParamDoc> pathParams = new ArrayList<>();
            List<ParamDoc> queryParams = new ArrayList<>();
            BodyDoc body = null;

            for (Parameter p : m.getParameters()) {
                String javaType = p.getType().asString();
                if (INFRA_PARAM_TYPES.contains(javaType)) {
                    continue;
                }

                ParamExtract extracted = extractParam(p);
                if (extracted.kind == ParamKind.HEADER) {
                    headers.add(extracted.param);
                } else if (extracted.kind == ParamKind.PATH) {
                    pathParams.add(extracted.param);
                } else if (extracted.kind == ParamKind.QUERY) {
                    queryParams.add(extracted.param);
                } else if (extracted.kind == ParamKind.BODY) {
                    if (body == null) {
                        body = new BodyDoc();
                        body.javaType = javaType;
                        body.uncertain = extracted.uncertain;
                    }
                } else {
                    if (body == null) {
                        body = new BodyDoc();
                        body.javaType = javaType;
                        body.uncertain = true;
                    }
                }
            }

            headers.add(defaultHeaderParam("App-Id"));
            headers.add(defaultHeaderParam("Tenant-Code"));
            if (!ignoreVerify) {
                headers.add(defaultHeaderParam("Authorization"));
            }

            ep.headers = dedupParams(headers);
            ep.pathParams = dedupParams(pathParams);
            ep.queryParams = dedupParams(queryParams);
            ep.body = body;
            ep.responseJavaType = m.getType().asString();

            endpoints.add(ep);
        }
        return endpoints;
    }

    private static List<ParamDoc> dedupParams(List<ParamDoc> in) {
        Map<String, ParamDoc> map = new LinkedHashMap<>();
        for (ParamDoc p : in) {
            if (p == null || p.name == null) {
                continue;
            }
            map.putIfAbsent(p.name, p);
        }
        return new ArrayList<>(map.values());
    }

    private static ParamDoc defaultHeaderParam(String name) {
        ParamDoc p = new ParamDoc();
        p.name = name;
        p.javaType = "String";
        p.required = false;
        return p;
    }

    private static String buildCurlExample(EndpointDoc ep) {
        String httpMethod = resolveCurlHttpMethod(ep.httpMethod);
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X ").append(httpMethod).append(" \\\n");
        sb.append("  \"{{baseUrl}}").append(ep.fullPath).append("\" \\\n");

        for (ParamDoc h : ep.headers) {
            if ("Authorization".equalsIgnoreCase(h.name)) {
                sb.append("  -H \"Authorization: {{authorization}}\" \\\n");
                continue;
            }
            if ("App-Id".equalsIgnoreCase(h.name)) {
                sb.append("  -H \"App-Id: {{appId}}\" \\\n");
                continue;
            }
            if ("Tenant-Code".equalsIgnoreCase(h.name)) {
                sb.append("  -H \"Tenant-Code: {{tenantCode}}\" \\\n");
                continue;
            }
        }

        if (ep.body != null) {
            sb.append("  -H \"Content-Type: application/json\" \\\n");
            sb.append("  --data '{{body}}'\n");
        } else {
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private static String resolveCurlHttpMethod(String httpMethod) {
        if (httpMethod == null || httpMethod.isBlank()) {
            return "POST";
        }
        String trimmed = httpMethod.trim();
        int commaIdx = trimmed.indexOf(',');
        if (commaIdx > 0) {
            return trimmed.substring(0, commaIdx).trim();
        }
        return trimmed;
    }

    private static Optional<String> resolveSummary(MethodDeclaration m) {
        String geelatoTest = getAnnotationStringValue(m, "GeelatoTest", "description");
        if (geelatoTest != null && !geelatoTest.isBlank()) {
            return Optional.of(geelatoTest.trim());
        }

        Optional<Javadoc> jd = m.getJavadoc();
        if (jd.isPresent()) {
            String text = jd.get().getDescription().toText().trim();
            if (!text.isBlank()) {
                String firstLine = text.split("\\R", 2)[0].trim();
                if (!firstLine.isBlank()) {
                    return Optional.of(firstLine);
                }
                return Optional.of(text);
            }
        }

        return Optional.empty();
    }

    private static ControllerAnnotationInfo findApiControllerInfo(ClassOrInterfaceDeclaration c) {
        if (hasAnnotationSimpleName(c, "ApiRuntimeRestController")) {
            ControllerAnnotationInfo info = new ControllerAnnotationInfo();
            info.conditional = "runtime（@ApiRuntimeRestController）";
            return info;
        }
        if (hasAnnotationSimpleName(c, "ApiRestController")) {
            ControllerAnnotationInfo info = new ControllerAnnotationInfo();
            info.conditional = "designtime（@ApiRestController）";
            info.category = getAnnotationStringValue(c, "ApiRestController", "category");
            return info;
        }
        return null;
    }

    private static MappingInfo parseMapping(AnnotationExpr ann) {
        MappingInfo info = new MappingInfo();
        String name = ann.getNameAsString();

        if ("GetMapping".equals(name)) {
            info.httpMethod = "GET";
        } else if ("PostMapping".equals(name)) {
            info.httpMethod = "POST";
        } else if ("PutMapping".equals(name)) {
            info.httpMethod = "PUT";
        } else if ("DeleteMapping".equals(name)) {
            info.httpMethod = "DELETE";
        } else if ("PatchMapping".equals(name)) {
            info.httpMethod = "PATCH";
        }

        List<String> paths = new ArrayList<>();
        paths.addAll(extractPathsFromAnnotationExpr(ann));
        info.paths = paths;

        if ("RequestMapping".equals(name) || "RuntimeMapping".equals(name)) {
            List<String> methods = extractEnumNames(ann, "method");
            if (!methods.isEmpty()) {
                info.httpMethod = methods.stream()
                        .map(GeneratorMain::mapSpringRequestMethod)
                        .collect(Collectors.joining(","));
            }
        }

        info.produces = extractStringList(ann, "produces").stream().findFirst().orElse(null);
        info.consumes = extractStringList(ann, "consumes").stream().findFirst().orElse(null);
        return info;
    }

    private static String mapSpringRequestMethod(String s) {
        if (s == null) {
            return null;
        }
        String t = s.toUpperCase(Locale.ROOT);
        if (t.endsWith(".GET") || "GET".equals(t)) {
            return "GET";
        }
        if (t.endsWith(".POST") || "POST".equals(t)) {
            return "POST";
        }
        if (t.endsWith(".PUT") || "PUT".equals(t)) {
            return "PUT";
        }
        if (t.endsWith(".DELETE") || "DELETE".equals(t)) {
            return "DELETE";
        }
        if (t.endsWith(".PATCH") || "PATCH".equals(t)) {
            return "PATCH";
        }
        return s;
    }

    private static ParamExtract extractParam(Parameter p) {
        if (hasAnnotationSimpleName(p, "PathVariable")) {
            ParamDoc doc = new ParamDoc();
            doc.name = firstNonBlank(
                    getAnnotationStringValue(p, "PathVariable", "value"),
                    getAnnotationStringValue(p, "PathVariable", "name"),
                    p.getNameAsString()
            );
            doc.javaType = p.getType().asString();
            doc.required = true;
            return ParamExtract.of(ParamKind.PATH, doc, false);
        }
        if (hasAnnotationSimpleName(p, "RequestParam")) {
            ParamDoc doc = new ParamDoc();
            doc.name = firstNonBlank(
                    getAnnotationStringValue(p, "RequestParam", "value"),
                    getAnnotationStringValue(p, "RequestParam", "name"),
                    p.getNameAsString()
            );
            doc.javaType = p.getType().asString();
            doc.required = getAnnotationBooleanValue(p, "RequestParam", "required");
            doc.defaultValue = getAnnotationStringValue(p, "RequestParam", "defaultValue");
            return ParamExtract.of(ParamKind.QUERY, doc, false);
        }
        if (hasAnnotationSimpleName(p, "RequestHeader")) {
            ParamDoc doc = new ParamDoc();
            doc.name = firstNonBlank(
                    getAnnotationStringValue(p, "RequestHeader", "value"),
                    getAnnotationStringValue(p, "RequestHeader", "name"),
                    p.getNameAsString()
            );
            doc.javaType = p.getType().asString();
            doc.required = getAnnotationBooleanValue(p, "RequestHeader", "required");
            doc.defaultValue = getAnnotationStringValue(p, "RequestHeader", "defaultValue");
            return ParamExtract.of(ParamKind.HEADER, doc, false);
        }
        if (hasAnnotationSimpleName(p, "RequestBody")) {
            ParamDoc doc = new ParamDoc();
            doc.name = p.getNameAsString();
            doc.javaType = p.getType().asString();
            return ParamExtract.of(ParamKind.BODY, doc, false);
        }
        ParamDoc doc = new ParamDoc();
        doc.name = p.getNameAsString();
        doc.javaType = p.getType().asString();
        return ParamExtract.of(ParamKind.UNKNOWN, doc, true);
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return null;
    }

    private static List<String> readRootModules(Path rootPom) throws Exception {
        var doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(rootPom.toFile());

        var modules = doc.getElementsByTagName("module");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < modules.getLength(); i++) {
            String text = modules.item(i).getTextContent();
            if (text != null && !text.isBlank()) {
                result.add(text.trim());
            }
        }
        return result;
    }

    private static Path resolveRepoRoot(String arg) {
        if (arg != null && !arg.isBlank()) {
            return Path.of(arg).toAbsolutePath().normalize();
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (isRepoRoot(cwd)) {
            return cwd;
        }
        if (cwd.getParent() != null && isRepoRoot(cwd.getParent())) {
            return cwd.getParent();
        }
        throw new IllegalArgumentException("无法定位仓库根目录，请使用 --repoRoot 指定");
    }

    private static boolean isRepoRoot(Path p) {
        Path pom = p.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return false;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(pom), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("<artifactId>geelato-community</artifactId>")) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private static List<Path> listJavaFiles(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        result.sort(Comparator.naturalOrder());
        return result;
    }

    private static AnnotationExpr findFirstMappingAnnotation(NodeWithAnnotations<?> node) {
        for (AnnotationExpr a : node.getAnnotations()) {
            if (MAPPING_ANNOTATIONS.contains(a.getNameAsString())) {
                return a;
            }
        }
        return null;
    }

    private static boolean hasAnyAnnotationSimpleName(NodeWithAnnotations<?> node, Set<String> names) {
        for (AnnotationExpr a : node.getAnnotations()) {
            if (names.contains(a.getNameAsString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnnotationSimpleName(NodeWithAnnotations<?> node, String name) {
        for (AnnotationExpr a : node.getAnnotations()) {
            if (name.equals(a.getNameAsString())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> extractPathsFromAnnotation(NodeWithAnnotations<?> node, String annName) {
        for (AnnotationExpr a : node.getAnnotations()) {
            if (!annName.equals(a.getNameAsString())) {
                continue;
            }
            return extractPathsFromAnnotationExpr(a);
        }
        return List.of();
    }

    private static List<String> extractPathsFromAnnotationExpr(AnnotationExpr ann) {
        List<String> values = new ArrayList<>();
        if (ann instanceof SingleMemberAnnotationExpr sma) {
            values.addAll(extractStringLiterals(sma.getMemberValue()));
            return values;
        }
        if (ann instanceof NormalAnnotationExpr na) {
            for (MemberValuePair p : na.getPairs()) {
                if ("value".equals(p.getNameAsString()) || "path".equals(p.getNameAsString())) {
                    values.addAll(extractStringLiterals(p.getValue()));
                }
            }
            return values;
        }
        return values;
    }

    private static List<String> extractStringList(AnnotationExpr ann, String key) {
        if (ann instanceof NormalAnnotationExpr na) {
            for (MemberValuePair p : na.getPairs()) {
                if (key.equals(p.getNameAsString())) {
                    return extractStringLiterals(p.getValue());
                }
            }
        }
        return List.of();
    }

    private static List<String> extractEnumNames(AnnotationExpr ann, String key) {
        if (ann instanceof NormalAnnotationExpr na) {
            for (MemberValuePair p : na.getPairs()) {
                if (!key.equals(p.getNameAsString())) {
                    continue;
                }
                Expression v = p.getValue();
                if (v.isFieldAccessExpr()) {
                    return List.of(v.asFieldAccessExpr().toString());
                }
                if (v.isArrayInitializerExpr()) {
                    List<String> r = new ArrayList<>();
                    for (Expression e : v.asArrayInitializerExpr().getValues()) {
                        r.add(e.toString());
                    }
                    return r;
                }
            }
        }
        return List.of();
    }

    private static List<String> extractStringLiterals(Expression e) {
        if (e.isStringLiteralExpr()) {
            return List.of(e.asStringLiteralExpr().asString());
        }
        if (e.isArrayInitializerExpr()) {
            ArrayInitializerExpr a = e.asArrayInitializerExpr();
            List<String> r = new ArrayList<>();
            for (Expression v : a.getValues()) {
                if (v.isStringLiteralExpr()) {
                    r.add(v.asStringLiteralExpr().asString());
                }
            }
            return r;
        }
        return List.of();
    }

    private static String getAnnotationStringValue(NodeWithAnnotations<?> node, String annName, String key) {
        for (AnnotationExpr a : node.getAnnotations()) {
            if (!annName.equals(a.getNameAsString())) {
                continue;
            }
            if (a instanceof SingleMemberAnnotationExpr sma) {
                if (!"value".equals(key)) {
                    return null;
                }
                return extractStringLiteral(sma.getMemberValue());
            }
            if (a instanceof NormalAnnotationExpr na) {
                for (MemberValuePair p : na.getPairs()) {
                    if (key.equals(p.getNameAsString())) {
                        return extractStringLiteral(p.getValue());
                    }
                }
            }
        }
        return null;
    }

    private static Boolean getAnnotationBooleanValue(NodeWithAnnotations<?> node, String annName, String key) {
        for (AnnotationExpr a : node.getAnnotations()) {
            if (!annName.equals(a.getNameAsString())) {
                continue;
            }
            if (a instanceof NormalAnnotationExpr na) {
                for (MemberValuePair p : na.getPairs()) {
                    if (key.equals(p.getNameAsString())) {
                        Expression v = p.getValue();
                        if (v.isBooleanLiteralExpr()) {
                            return v.asBooleanLiteralExpr().getValue();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String extractStringLiteral(Expression e) {
        if (e instanceof StringLiteralExpr s) {
            return s.asString();
        }
        return null;
    }

    private static List<String> normalizePaths(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> r = raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.startsWith("/") ? s : "/" + s)
                .distinct()
                .toList();
        return r;
    }

    private static String joinPath(String base, String path) {
        String a = base == null ? "" : base.trim();
        String b = path == null ? "" : path.trim();
        if (a.isBlank()) {
            return normalizeJoin("", b);
        }
        if (b.isBlank()) {
            return normalizeJoin(a, "");
        }
        return normalizeJoin(a, b);
    }

    private static String normalizeJoin(String a, String b) {
        String left = a.endsWith("/") ? a.substring(0, a.length() - 1) : a;
        String right = b.startsWith("/") ? b : "/" + b;
        String full = left + right;
        full = full.replaceAll("/{2,}", "/");
        if (!full.startsWith("/")) {
            full = "/" + full;
        }
        return full;
    }

    private static String fileUriWithRange(Path file, NodeWithRange<?> node) {
        String uri = toFileUri(file);
        if (node.getRange().isEmpty()) {
            return uri;
        }
        int begin = node.getRange().get().begin.line;
        int end = node.getRange().get().end.line;
        return uri + "#L" + begin + "-L" + end;
    }

    private static String toFileUri(Path p) {
        String abs = p.toAbsolutePath().normalize().toString().replace('\\', '/');
        if (abs.matches("^[A-Za-z]:/.*")) {
            return "file:///" + abs;
        }
        return "file://" + abs;
    }

    private static void ensureDir(Path p) {
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeUtf8(Path path, String content) {
        try {
            ensureDir(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeJson(Path path, Object obj) {
        try {
            ensureDir(path.getParent());
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
            Files.writeString(path, json + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class CliArgs {
        final String repoRoot;

        private CliArgs(String repoRoot) {
            this.repoRoot = repoRoot;
        }

        static CliArgs parse(String[] args) {
            String repoRoot = null;
            for (int i = 0; i < args.length; i++) {
                if ("--repoRoot".equals(args[i]) && i + 1 < args.length) {
                    repoRoot = args[++i];
                }
            }
            return new CliArgs(repoRoot);
        }
    }

    private static final class ControllerDoc {
        String module;
        String controllerName;
        String controllerFqn;
        String sourceLink;
        List<String> basePaths = List.of();
        String category;
        String conditional;
        List<EndpointDoc> endpoints = List.of();
    }

    private static final class EndpointDoc {
        String module;
        String controllerName;
        String controllerFqn;
        String methodName;
        String methodLink;
        String summary;
        String httpMethod;
        List<String> relativePaths = List.of();
        String fullPath;
        String produces;
        String consumes;
        String auth;
        List<ParamDoc> headers = List.of();
        List<ParamDoc> pathParams = List.of();
        List<ParamDoc> queryParams = List.of();
        BodyDoc body;
        String responseJavaType;
        String curlExample;

        EndpointDoc copy() {
            EndpointDoc x = new EndpointDoc();
            x.module = module;
            x.controllerName = controllerName;
            x.controllerFqn = controllerFqn;
            x.methodName = methodName;
            x.methodLink = methodLink;
            x.summary = summary;
            x.httpMethod = httpMethod;
            x.relativePaths = relativePaths;
            x.fullPath = fullPath;
            x.produces = produces;
            x.consumes = consumes;
            x.auth = auth;
            x.headers = headers;
            x.pathParams = pathParams;
            x.queryParams = queryParams;
            x.body = body;
            x.responseJavaType = responseJavaType;
            x.curlExample = curlExample;
            return x;
        }
    }

    private static final class ParamDoc {
        String name;
        String javaType;
        Boolean required;
        String defaultValue;
        String description;
    }

    private static final class BodyDoc {
        String javaType;
        boolean uncertain;
    }

    private static final class MappingInfo {
        List<String> paths = List.of();
        String httpMethod;
        String produces;
        String consumes;
    }

    private static final class ControllerAnnotationInfo {
        String category;
        String conditional;
    }

    private enum ParamKind {
        HEADER, PATH, QUERY, BODY, UNKNOWN
    }

    private static final class ParamExtract {
        final ParamKind kind;
        final ParamDoc param;
        final boolean uncertain;

        private ParamExtract(ParamKind kind, ParamDoc param, boolean uncertain) {
            this.kind = kind;
            this.param = param;
            this.uncertain = uncertain;
        }

        static ParamExtract of(ParamKind kind, ParamDoc param, boolean uncertain) {
            return new ParamExtract(kind, param, uncertain);
        }
    }
}
