package dev.parseforge.infrastructure.process;
import java.nio.file.Path;
import java.nio.file.Files;

public final class WindowsJobFixture {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("child")) { Thread.sleep(120000); return; }
        if (!WindowsApplicationJob.initialize()) throw new IllegalStateException("Job not enabled");
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),
                "-cp",System.getProperty("java.class.path"),WindowsJobFixture.class.getName(),"child").start();
        Files.writeString(Path.of(args[1]),Long.toString(child.pid()));
        Thread.sleep(120000);
    }
}
