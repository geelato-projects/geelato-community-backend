package cn.geelato.mcp.fms.tool;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ContainerQueryToolTest {

    @Autowired
    private ContainerQueryTool containerQueryTool;

    @Test
    void testQueryContainerLocation() {
        String result = containerQueryTool.queryContainerLocation("CONT123456");
        assertNotNull(result);
        assertTrue(result.contains("CONT123456"));
        assertTrue(result.contains("位置"));
    }

    @Test
    void testQueryFreightContainers() {
        String result = containerQueryTool.queryFreightContainers("FREIGHT001");
        assertNotNull(result);
        assertTrue(result.contains("FREIGHT001"));
        assertTrue(result.contains("集装箱列表"));
    }

    @Test
    void testQueryContainerTrack() {
        String result = containerQueryTool.queryContainerTrack("CONT789012");
        assertNotNull(result);
        assertTrue(result.contains("CONT789012"));
        assertTrue(result.contains("运输轨迹"));
    }
}
