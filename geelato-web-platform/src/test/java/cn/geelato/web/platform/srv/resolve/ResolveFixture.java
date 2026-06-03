package cn.geelato.web.platform.srv.resolve;

import java.nio.file.Path;
import java.nio.file.Paths;

final class ResolveFixture {
    private ResolveFixture() {
    }

    static Path ooclSoPdf() {
        Path moduleDir = Paths.get(System.getProperty("user.dir"));
        Path rootDir = moduleDir.getParent().getParent();
        return rootDir.resolve("船司SO PDF").resolve("OOCL").resolve("2305042743.pdf");
    }
}

