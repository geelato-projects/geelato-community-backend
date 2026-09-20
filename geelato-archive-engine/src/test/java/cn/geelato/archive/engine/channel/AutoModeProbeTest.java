package cn.geelato.archive.engine.channel;

import cn.geelato.archive.enums.ExecutionModeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** AUTO 模式探测的纯逻辑测试（URL 解析 + 同实例判定，不连库） */
class AutoModeProbeTest {

    private static final String SRC = "jdbc:mysql://172.25.192.19:4489/geelato?useSSL=false";

    @Test
    void sameUrlIsServerMode() {
        assertEquals(ExecutionModeEnum.SERVER, AutoModeProbe.probe(SRC, SRC));
    }

    @Test
    void blankTargetMeansPrimaryItself() {
        assertEquals(ExecutionModeEnum.SERVER, AutoModeProbe.probe(SRC, null));
        assertEquals(ExecutionModeEnum.SERVER, AutoModeProbe.probe(SRC, ""));
    }

    @Test
    void sameInstanceDifferentSchemaIsServerMode() {
        String target = "jdbc:mysql://172.25.192.19:4489/geelato_archive?useSSL=false";
        assertEquals(ExecutionModeEnum.SERVER, AutoModeProbe.probe(SRC, target));
    }

    @Test
    void crossInstancePrefersLocalFile() {
        String target = "jdbc:mysql://172.25.192.26:3306/geelato_archive";
        assertEquals(ExecutionModeEnum.LOCAL_FILE, AutoModeProbe.probe(SRC, target));
    }

    @Test
    void sameHostDifferentPortIsCrossInstance() {
        String target = "jdbc:mysql://172.25.192.19:3307/geelato_archive";
        assertEquals(ExecutionModeEnum.LOCAL_FILE, AutoModeProbe.probe(SRC, target));
    }

    @Test
    void unparseableUrlFallsBackToJdbc() {
        assertEquals(ExecutionModeEnum.JDBC, AutoModeProbe.probe(SRC, "not-a-jdbc-url"));
    }

    @Test
    void hostPortExtraction() {
        assertEquals("172.25.192.19:4489", AutoModeProbe.hostPort(SRC));
        assertEquals("localhost:3306", AutoModeProbe.hostPort("jdbc:mysql://localhost:3306/db"));
        assertEquals(null, AutoModeProbe.hostPort("jdbc:postgresql://localhost/db"));
        assertEquals(null, AutoModeProbe.hostPort(null));
    }
}
