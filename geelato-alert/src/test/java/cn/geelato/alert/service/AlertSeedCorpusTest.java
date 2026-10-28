package cn.geelato.alert.service;

import cn.geelato.alert.entity.FmsAlertRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 告警规则种子目录契约校验（bea/alert-rules/*.yaml，仓库外语料）。
 *
 * 每条种子按真实导入链路（YAML → RuleDocument → applyDocument 规范化 → contractCheck）
 * 验证可通过启用门禁；种子目录不在本仓库内，缺失时跳过。
 */
class AlertSeedCorpusTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** surefire 工作目录=模块根；bea 种子目录在仓库外上级（geelato-enterprise/bea）。 */
    private static final Path SEED_DIR = Paths.get("..", "..", "bea", "alert-rules");

    private final AlertRuleService service = new AlertRuleService();

    @Test
    @DisplayName("种子目录全部 YAML 通过启用契约门禁（含 source/通道码校验）")
    void allSeedsPassContractCheck() throws Exception {
        assumeTrue(Files.isDirectory(SEED_DIR), "种子目录不存在，跳过: " + SEED_DIR.toAbsolutePath());
        List<Path> files;
        try (Stream<Path> stream = Files.list(SEED_DIR)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".yaml")).sorted().toList();
        }
        assertFalse(files.isEmpty(), "种子目录不应为空: " + SEED_DIR.toAbsolutePath());
        List<String> failures = new ArrayList<>();
        for (Path file : files) {
            Object loaded = new Yaml().load(Files.readString(file));
            JsonNode doc = MAPPER.valueToTree(loaded);
            FmsAlertRule rule = new FmsAlertRule();
            service.applyDocument(rule, doc);
            List<Map<String, Object>> issues = service.contractCheck(rule);
            if (!issues.isEmpty()) {
                failures.add(file.getFileName() + ": " + issues);
            }
        }
        assertTrue(failures.isEmpty(), "以下种子未通过契约门禁:\n" + String.join("\n", failures));
    }
}
