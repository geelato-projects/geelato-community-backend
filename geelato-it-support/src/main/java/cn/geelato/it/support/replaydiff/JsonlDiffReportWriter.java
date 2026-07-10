package cn.geelato.it.support.replaydiff;

import cn.geelato.it.support.json.ObjectMappers;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class JsonlDiffReportWriter implements Closeable {
    private final BufferedWriter writer;

    public JsonlDiffReportWriter(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.writer = Files.newBufferedWriter(
                    file,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open report file: " + file, e);
        }
    }

    public void write(DiffReport report) {
        if (report == null) {
            return;
        }
        try {
            String line = ObjectMappers.defaultMapper().writeValueAsString(report);
            synchronized (writer) {
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write report", e);
        }
    }

    @Override
    public void close() {
        try {
            synchronized (writer) {
                writer.close();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
