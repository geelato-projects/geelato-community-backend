package cn.geelato.script.boot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 整库注入声明解析：约定文件发现合并 / 别名冲突 / exclude 合并。 */
class HostKitDescriptorsTest {

    @Test
    void descriptorFileDiscoveryAndExclude(@TempDir Path tempDir) throws Exception {
        Path metaInf = tempDir.resolve("META-INF/gl");
        Files.createDirectories(metaInf);
        Files.writeString(metaInf.resolve("script-kits.properties"),
                "# 约定文件\ntestlib=cn.geelato.script.hostfixture.util\nexclude=DateLikeService\n");

        try (URLClassLoader cl = new URLClassLoader(new URL[]{tempDir.toUri().toURL()},
                Thread.currentThread().getContextClassLoader())) {
            assertEquals(Map.of("testlib", "cn.geelato.script.hostfixture.util"),
                    HostKitDescriptors.resolveAliases(cl));
            assertEquals(List.of("DateLikeService"), HostKitDescriptors.resolveExcludes(cl));
        }
    }

    @Test
    void aliasConflictAcrossFilesFailsFast(@TempDir Path dirA, @TempDir Path dirB) throws Exception {
        writeDescriptor(dirA, "dup=cn.a");
        writeDescriptor(dirB, "dup=cn.b");

        try (URLClassLoader cl = new URLClassLoader(new URL[]{dirA.toUri().toURL(), dirB.toUri().toURL()},
                Thread.currentThread().getContextClassLoader())) {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> HostKitDescriptors.resolveAliases(cl));
            assertTrue(ex.getMessage().contains("别名冲突"), ex.getMessage());
            assertTrue(ex.getMessage().contains("cn.a") && ex.getMessage().contains("cn.b"), ex.getMessage());
        }
    }

    @Test
    void sameAliasSamePackageAcrossFilesIsIdempotent(@TempDir Path dirA, @TempDir Path dirB) throws Exception {
        writeDescriptor(dirA, "same=cn.x.Y\nexclude=One");
        writeDescriptor(dirB, "same=cn.x.Y\nexclude=Two");

        try (URLClassLoader cl = new URLClassLoader(new URL[]{dirA.toUri().toURL(), dirB.toUri().toURL()},
                Thread.currentThread().getContextClassLoader())) {
            assertEquals(Map.of("same", "cn.x.Y"), HostKitDescriptors.resolveAliases(cl));
            assertEquals(List.of("One", "Two"), HostKitDescriptors.resolveExcludes(cl));
        }
    }

    private static void writeDescriptor(Path dir, String content) throws Exception {
        Path metaInf = dir.resolve("META-INF/gl");
        Files.createDirectories(metaInf);
        Files.writeString(metaInf.resolve("script-kits.properties"), content);
    }
}
