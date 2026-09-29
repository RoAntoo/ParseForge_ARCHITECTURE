package dev.parseforge.application.port.out;

public interface ProcessExecutor {
    ProcessResult execute(ProcessSpec spec, ProcessOutputListener listener);

    void cancel();
}
