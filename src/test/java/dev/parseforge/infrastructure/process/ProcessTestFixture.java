package dev.parseforge.infrastructure.process;

public final class ProcessTestFixture {
    private ProcessTestFixture() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("child-normal") || args[0].equals("child-cancel"))) {
            String binary = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
            Process child = new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"), "bin", binary).toString(),
                    "-cp", System.getProperty("java.class.path"), ProcessTestFixture.class.getName(), "wait").inheritIO().start();
            System.out.println("child-pid:" + child.pid());
            System.out.flush();
            Thread.sleep(args[0].equals("child-normal") ? 500 : 30_000);
            return;
        }
        if (args.length > 0 && args[0].equals("environment")) {
            System.out.println("ONLY:" + System.getenv("PARSEFORGE_TEST_ENV"));
            System.out.println("PATH:" + System.getenv("PATH"));
            return;
        }
        System.out.println("stdout-line");
        System.err.println("stderr-line");
        if (args.length > 0 && args[0].equals("many-lines")) {
            String payload = "x".repeat(200);
            for (int index = 0; index < 20_000; index++) {
                System.out.println(index + ":" + payload);
            }
        }
        if (args.length > 0 && args[0].equals("wait")) {
            System.out.println("ready");
            System.out.flush();
            Thread.sleep(30_000);
        }
    }
}
