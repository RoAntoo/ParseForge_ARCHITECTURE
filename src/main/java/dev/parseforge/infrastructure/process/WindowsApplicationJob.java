package dev.parseforge.infrastructure.process;

import com.sun.jna.*;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.win32.StdCallLibrary;

/** Owns a non-inheritable handle until OS process teardown, including hard crashes. */
public final class WindowsApplicationJob {
    private static HANDLE job;
    private interface Jobs extends StdCallLibrary {
        Jobs INSTANCE = Native.load("kernel32", Jobs.class);
        HANDLE CreateJobObjectW(Pointer attributes, WString name);
        boolean SetInformationJobObject(HANDLE job, int kind, Pointer data, int length);
        boolean AssignProcessToJobObject(HANDLE job, HANDLE process);
    }
    private WindowsApplicationJob() { }
    public static synchronized boolean initialize() {
        if (!System.getProperty("os.name").startsWith("Windows")) return false;
        if (job != null) return true;
        if (Native.POINTER_SIZE != 8) throw new IllegalStateException("ParseForge requiere Windows x64.");
        HANDLE handle = Jobs.INSTANCE.CreateJobObjectW(null, null);
        if (handle == null) throw failure("CreateJobObject");
        // Windows x64 JOBOBJECT_EXTENDED_LIMIT_INFORMATION: 144 bytes,
        // BasicLimitInformation.LimitFlags at byte 16. No breakaway flags.
        try (Memory limits = new Memory(144)) {
            limits.clear(); limits.setInt(16, 0x00002000); // KILL_ON_JOB_CLOSE
            if (!Jobs.INSTANCE.SetInformationJobObject(handle, 9, limits, (int) limits.size())) {
                var error = failure("SetInformationJobObject"); Kernel32.INSTANCE.CloseHandle(handle); throw error;
            }
            if (!Jobs.INSTANCE.AssignProcessToJobObject(handle, Kernel32.INSTANCE.GetCurrentProcess())) {
                var error = failure("AssignProcessToJobObject"); Kernel32.INSTANCE.CloseHandle(handle); throw error;
            }
        }
        job = handle;
        // Never close explicitly: doing so would terminate this JVM before settings/logs flush.
        return true;
    }
    private static IllegalStateException failure(String operation) {
        return new IllegalStateException(operation + " failed: Win32 " + Native.getLastError());
    }
}
