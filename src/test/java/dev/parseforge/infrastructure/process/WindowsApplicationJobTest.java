package dev.parseforge.infrastructure.process;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WindowsApplicationJobTest {
    @TempDir Path temp;
    @Test void hardJvmTerminationKillsInheritedChildWithoutCooperativeCleanup() throws Exception {
        Path pidFile = temp.resolve("child.pid");
        Process parent = new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),
                "-cp",System.getProperty("java.class.path"),WindowsJobFixture.class.getName(),"parent",pidFile.toString())
                .redirectError(temp.resolve("stderr.txt").toFile()).start();
        ProcessHandle child = null;
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(pidFile) && parent.isAlive() && System.nanoTime() < deadline) Thread.sleep(50);
            assertTrue(Files.exists(pidFile), () -> {
                try { return Files.readString(temp.resolve("stderr.txt")); } catch(Exception e) { return e.toString(); }
            });
            child = ProcessHandle.of(Long.parseLong(Files.readString(pidFile))).orElseThrow();
            assertTrue(child.isAlive());
            parent.destroyForcibly(); assertTrue(parent.waitFor(5,TimeUnit.SECONDS));
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (child.isAlive() && System.nanoTime() < deadline) Thread.sleep(50);
            assertFalse(child.isAlive(), "Job must remove descendants after hard JVM termination");
        } finally { parent.destroyForcibly(); if(child!=null && child.isAlive()) child.destroyForcibly(); }
    }
}
