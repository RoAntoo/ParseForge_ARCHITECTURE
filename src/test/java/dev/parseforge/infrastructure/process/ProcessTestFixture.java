package dev.parseforge.infrastructure.process;

public final class ProcessTestFixture {
    private ProcessTestFixture() {
    }

    public static void main(String[] args) throws Exception {
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
