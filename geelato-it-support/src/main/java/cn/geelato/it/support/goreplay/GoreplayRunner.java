package cn.geelato.it.support.goreplay;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class GoreplayRunner {
    private final String executable;
    private final Path workingDirectory;
    private final Map<String, String> environment;

    public GoreplayRunner() {
        this("goreplay", null, Map.of());
    }

    public GoreplayRunner(String executable, Path workingDirectory, Map<String, String> environment) {
        this.executable = executable == null || executable.isBlank() ? "goreplay" : executable;
        this.workingDirectory = workingDirectory;
        this.environment = environment == null ? Map.of() : Map.copyOf(environment);
    }

    public Optional<Path> resolveExecutable() {
        return findInPath(executable);
    }

    public boolean isAvailable() {
        return resolveExecutable().isPresent();
    }

    public Process start(List<String> args) {
        List<String> command = new ArrayList<>();
        command.add(resolveExecutable().map(Path::toString).orElse(executable));
        if (args != null) {
            command.addAll(args);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDirectory != null) {
            pb.directory(workingDirectory.toFile());
        }
        pb.redirectErrorStream(true);
        pb.environment().putAll(environment);
        try {
            return pb.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start goreplay process", e);
        }
    }

    public int runAndWait(List<String> args, Duration timeout) {
        Process process = start(args);
        try {
            if (timeout == null) {
                return process.waitFor();
            }
            boolean done = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!done) {
                process.destroyForcibly();
                return -1;
            }
            return process.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return -1;
        }
    }

    public static Optional<Path> findInPath(String executable) {
        if (executable == null || executable.isBlank()) {
            return Optional.empty();
        }
        Path direct = Paths.get(executable);
        if (direct.isAbsolute() && Files.isRegularFile(direct)) {
            return Optional.of(direct);
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }
        String[] segments = pathEnv.split(File.pathSeparator);
        String exe = executable;
        boolean hasExt = exe.toLowerCase(Locale.ROOT).endsWith(".exe");
        for (String seg : segments) {
            if (seg == null || seg.isBlank()) {
                continue;
            }
            Path candidate = Paths.get(seg).resolve(exe);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
            if (!hasExt) {
                Path candidateExe = Paths.get(seg).resolve(exe + ".exe");
                if (Files.isRegularFile(candidateExe)) {
                    return Optional.of(candidateExe);
                }
            }
        }
        return Optional.empty();
    }

    public static void assumeGoreplayAvailable(String executable) {
        Optional<Path> resolved = findInPath(Objects.requireNonNullElse(executable, "goreplay"));
        boolean ok = resolved.isPresent();
        String message = ok ? "" : "goreplay not found in PATH, skip";
        invokeJunitAssumeTrue(ok, message);
        if (!ok) {
            throw new IllegalStateException(message);
        }
    }

    public static void assumeGoreplayAvailable() {
        assumeGoreplayAvailable("goreplay");
    }

    private static void invokeJunitAssumeTrue(boolean condition, String message) {
        try {
            Class<?> assumptions = Class.forName("org.junit.jupiter.api.Assumptions");
            Method assumeTrue = assumptions.getMethod("assumeTrue", boolean.class, String.class);
            assumeTrue.invoke(null, condition, message);
        } catch (ClassNotFoundException e) {
            return;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
