package dev.parseforge.infrastructure.process;

import com.sun.jna.platform.win32.Kernel32;
import dev.parseforge.domain.exception.EngineInstallException;

public final class WindowsNativePrerequisites {
    private WindowsNativePrerequisites() { }
    public static void requireMarkerRuntime() {
        if (!System.getProperty("os.name").startsWith("Windows")) return;
        for (String dll : new String[]{"vcruntime140.dll", "vcruntime140_1.dll", "msvcp140.dll"}) {
            var module = Kernel32.INSTANCE.LoadLibraryEx(dll, null, 0x00000800); // System32 only
            if (module == null) throw new EngineInstallException(
                    EngineInstallException.Code.HEALTH_CHECK_FAILED,
                    "Marker necesita Microsoft Visual C++ Runtime x64. Instalá el paquete oficial, "
                    + "volvé a abrir ParseForge y reintentá. https://aka.ms/vs/17/release/vc_redist.x64.exe");
            Kernel32.INSTANCE.FreeLibrary(module);
        }
    }
}
