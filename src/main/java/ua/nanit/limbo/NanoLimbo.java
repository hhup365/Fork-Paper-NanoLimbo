import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App {

    // --- 环境配置读取 ---
    private static final Map<String, String> envVars = new HashMap<>();

    static {
        loadDotEnv();
    }

    private static void loadDotEnv() {
        File dotenv = new File(".env");
        if (dotenv.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(dotenv))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        envVars.put(parts[0].trim(), parts[1].trim());
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static String getEnv(String key, String defaultValue) {
        String val = System.getenv(key);
        if (val == null || val.isEmpty()) {
            val = envVars.get(key);
        }
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }

    // --- 全局变量配置 ---
    private static final String UPLOAD_URL = getEnv("UPLOAD_URL", "");
    private static final String PROJECT_URL = getEnv("PROJECT_URL", "");
    private static final boolean AUTO_ACCESS = "true".equalsIgnoreCase(getEnv("AUTO_ACCESS", "false"));
    private static final String FILE_PATH = getEnv("FILE_PATH", ".cache");
    private static final String SUB_PATH = getEnv("SUB_PATH", "sub");
    private static final String UUID = getEnv("UUID", "f929c4da-dc2e-4e0d-9a6f-1799036af214");
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

    // 端口变量赋值
    private static final Integer S5_PORT = parsePort(S5_PORT_STR);
    private static final Integer TUIC_PORT = parsePort(TUIC_PORT_STR);
    private static final Integer HY2_PORT = parsePort(HY2_PORT_STR);
    private static final Integer ANYTLS_PORT = parsePort(ANYTLS_PORT_STR);
    private static final Integer REALITY_PORT = parsePort(REALITY_PORT_STR);
    private static final Integer ANYREALITY_PORT = parsePort(ANYREALITY_PORT_STR);

    private static String private_key = "";
    private static String public_key = "";

    // 路径配置
    private static final String npm_path = FILE_PATH + "/npm";
    private static final String php_path = FILE_PATH + "/php";
    private static final String web_path = FILE_PATH + "/web";
    private static final String bot_path = FILE_PATH + "/bot";
    private static final String sub_path = FILE_PATH + "/sub.txt";
    private static final String list_path = FILE_PATH + "/list.txt";
    private static final String boot_log_path = FILE_PATH + "/boot.log";
    private static final String config_path = FILE_PATH + "/config.json";

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static Integer parsePort(String portStr) {
        if (portStr != null && portStr.matches("\\d+")) {
            return Integer.parseInt(portStr);
        }
        return null;
    }

    // --- 工具类与核心方法 ---

    private static void createDirectory() {
        System.out.print("\033c");
        System.out.flush();
        File dir = new File(FILE_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println(FILE_PATH + " is created");
        } else {
            System.out.println(FILE_PATH + " already exists");
        }
    }

    private static void deleteNodes() {
        if (UPLOAD_URL.isEmpty() || !new File(sub_path).exists()) return;
        try {
            String content = Files.readString(Paths.get(sub_path));
            String decoded = new String(Base64.getDecoder().decode(content.trim()), StandardCharsets.UTF_8);
            
            List<String> nodes = new ArrayList<>();
            for (String line : decoded.split("\n")) {
                if (line.contains("vless://") || line.contains("vmess://") || line.contains("trojan://") ||
                    line.contains("hysteria2://") || line.contains("tuic://") || line.contains("anytls://") || line.contains("socks://")) {
                    nodes.add(line);
                }
            }
            if (nodes.isEmpty()) return;

            Map<String, Object> reqData = new HashMap<>();
            reqData.put("nodes", nodes);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(UPLOAD_URL + "/api/delete-nodes"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(reqData)))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.out.println("Error in delete_nodes: " + e.getMessage());
        }
    }

    private static void cleanupOldFiles() {
        String[] paths = {"web", "bot", "npm", "boot.log", "list.txt"};
        for (String file : paths) {
            File f = new File(FILE_PATH, file);
            try {
                if (f.exists()) {
                    if (f.isDirectory()) {
                        Files.walk(f.toPath())
                             .sorted(Comparator.reverseOrder())
                             .map(Path::toFile)
                             .forEach(File::delete);
                    } else {
                        f.delete();
                    }
                }
            } catch (Exception e) {
                System.out.println("Error removing " + f.getPath() + ": " + e.getMessage());
            }
        }
    }

    private static String getSystemArchitecture() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("arm") || arch.contains("aarch64")) {
            return "arm";
        }
        return "amd";
    }

    private static boolean downloadFile(String fileName, String fileUrl) {
        Path path = Paths.get(FILE_PATH, fileName);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fileUrl))
                    .GET()
                    .build();
            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(path));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("Download " + fileName + " successfully");
                return true;
            } else {
                Files.deleteIfExists(path);
                System.out.println("Download " + fileName + " failed: HTTP " + response.statusCode());
                return false;
            }
        } catch (Exception e) {
            try { Files.deleteIfExists(path); } catch (Exception ignored) {}
            System.out.println("Download " + fileName + " failed: " + e.getMessage());
            return false;
        }
    }

    private static List<Map<String, String>> getFilesForArchitecture(String architecture) {
        List<Map<String, String>> baseFiles = new ArrayList<>();
        Map<String, String> web = new HashMap<>();
        web.put("fileName", "web");
        web.put("fileUrl", "arm".equals(architecture) ? "https://arm64.ssss.nyc.mn/sb" : "https://amd64.ssss.nyc.mn/sb");
        baseFiles.add(web);

        Map<String, String> bot = new HashMap<>();
        bot.put("fileName", "bot");
        bot.put("fileUrl", "arm".equals(architecture) ? "https://arm64.ssss.nyc.mn/2go" : "https://amd64.ssss.nyc.mn/2go");
        baseFiles.add(bot);

        if (!NEZHA_SERVER.isEmpty() && !NEZHA_KEY.isEmpty()) {
            Map<String, String> nezhaMap = new HashMap<>();
            if (!NEZHA_PORT.isEmpty()) {
                nezhaMap.put("fileName", "npm");
                nezhaMap.put("fileUrl", "arm".equals(architecture) ? "https://arm64.ssss.nyc.mn/agent" : "https://amd64.ssss.nyc.mn/agent");
            } else {
                nezhaMap.put("fileName", "php");
                nezhaMap.put("fileUrl", "arm".equals(architecture) ? "https://arm64.ssss.nyc.mn/v1" : "https://amd64.ssss.nyc.mn/v1");
            }
            baseFiles.add(0, nezhaMap);
        }
        return baseFiles;
    }

    private static void authorizeFiles(List<String> filePaths) {
        for (String relative : filePaths) {
            File f = new File(FILE_PATH, relative);
            if (f.exists()) {
                try {
                    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwxr-x");
                    Files.setPosixFilePermissions(f.toPath(), perms);
                    System.out.println("Empowerment success for " + f.getAbsolutePath() + ": 775");
                } catch (Exception e) {
                    f.setExecutable(true);
                    System.out.println("Empowerment fallback for " + f.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }
    }

    private static void argoType() {
        if (DISABLE_ARGO) {
            System.out.println("DISABLE_ARGO is set to true, disable argo tunnel");
            return;
        }
        if (ARGO_AUTH.isEmpty() || ARGO_DOMAIN.isEmpty()) {
            System.out.println("ARGO_DOMAIN or ARGO_AUTH variable is empty, use quick tunnels");
            return;
        }

        if (ARGO_AUTH.contains("TunnelSecret")) {
            try {
                Files.writeString(Paths.get(FILE_PATH, "tunnel.json"), ARGO_AUTH);
                String[] parts = ARGO_AUTH.split("\"");
                String tunnelId = parts.length > 11 ? parts[11] : "unknown";

                String tunnelYml = String.format(
                        "tunnel: %s\n" +
                        "credentials-file: %s/tunnel.json\n" +
                        "protocol: http2\n\n" +
                        "ingress:\n" +
                        "  - hostname: %s\n" +
                        "    service: http://localhost:%d\n" +
                        "    originRequest:\n" +
                        "      noTLSVerify: true\n" +
                        "  - service: http_status:404\n",
                        tunnelId, FILE_PATH, ARGO_DOMAIN, ARGO_PORT
                );
                Files.writeString(Paths.get(FILE_PATH, "tunnel.yml"), tunnelYml);
            } catch (Exception e) {
                System.out.println("Error creating tunnel config: " + e.getMessage());
            }
        } else {
            System.out.println("ARGO_AUTH mismatch TunnelSecret, use token connect to tunnel");
        }
    }

    private static String execCmd(String command) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
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
            System.out.println("Error executing command: " + e.getMessage());
            return e.getMessage();
        }
        return output.toString();
    }

    private static void downloadFilesAndRun() throws Exception {
        String architecture = getSystemArchitecture();
        List<Map<String, String>> files = getFilesForArchitecture(architecture);

        if (files.isEmpty()) {
            System.out.println("Can't find a file for the current architecture");
            return;
        }

        boolean success = true;
        for (Map<String, String> info : files) {
            if (!downloadFile(info.get("fileName"), info.get("fileUrl"))) {
                success = false;
            }
        }

        if (!success) {
            System.out.println("Error downloading files");
            return;
        }

        List<String> toAuthorize = !NEZHA_PORT.isEmpty() ? 
                Arrays.asList("npm", "web", "bot") : Arrays.asList("php", "web", "bot");
        authorizeFiles(toAuthorize);

        String nezhaTls = "false";
        String[] parts = NEZHA_SERVER.split(":");
        String port = parts.length > 1 ? parts[parts.length - 1] : "";
        if (Arrays.asList("443", "8443", "2096", "2087", "2083", "2053").contains(port)) {
            nezhaTls = "tls";
        }

        if (!NEZHA_SERVER.isEmpty() && !NEZHA_KEY.isEmpty() && NEZHA_PORT.isEmpty()) {
            String configYaml = String.format(
                    "client_secret: %s\n" +
                    "debug: false\n" +
                    "disable_auto_update: true\n" +
                    "disable_command_execute: false\n" +
                    "disable_force_update: true\n" +
                    "disable_nat: false\n" +
                    "disable_send_query: false\n" +
                    "gpu: false\n" +
                    "insecure_tls: true\n" +
                    "ip_report_period: 1800\n" +
                    "report_delay: 4\n" +
                    "server: %s\n" +
                    "skip_connection_count: true\n" +
                    "skip_procs_count: true\n" +
                    "temperature: false\n" +
                    "tls: %s\n" +
                    "use_gitee_to_upgrade: false\n" +
                    "use_ipv6_country_code: false\n" +
                    "uuid: %s",
                    NEZHA_KEY, NEZHA_SERVER, nezhaTls, UUID);
            Files.writeString(Paths.get(FILE_PATH, "config.yaml"), configYaml);
        }

        String keypairOut = execCmd(FILE_PATH + "/web generate reality-keypair");
        Matcher privMatcher = Pattern.compile("PrivateKey:\\s*(.*)").matcher(keypairOut);
        Matcher pubMatcher = Pattern.compile("PublicKey:\\s*(.*)").matcher(keypairOut);

        if (privMatcher.find() && pubMatcher.find()) {
            private_key = privMatcher.group(1).trim();
            public_key = pubMatcher.group(1).trim();
            System.out.println("Private Key: " + private_key);
            System.out.println("Public Key: " + public_key);
        } else {
            System.out.println("Failed to extract privateKey or publicKey from output.");
            return;
        }

        execCmd(String.format("openssl ecparam -genkey -name prime256v1 -out \"%s/private.key\"", FILE_PATH));
        execCmd(String.format("openssl req -new -x509 -days 3650 -key \"%s/private.key\" -out \"%s/cert.pem\" -subj \"/CN=bing.com\"", FILE_PATH, FILE_PATH));

        // 构造 config.json 数据结构
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("log", Map.of("disabled", true, "level", "info", "timestamp", true));
        
        List<Map<String, Object>> inbounds = new ArrayList<>();
        Map<String, Object> vmessIn = new LinkedHashMap<>();
        vmessIn.put("tag", "vmess-ws-in");
        vmessIn.put("type", "vmess");
        vmessIn.put("listen", "::");
        vmessIn.put("listen_port", ARGO_PORT);
        vmessIn.put("users", List.of(Map.of("uuid", UUID)));
        vmessIn.put("transport", Map.of("type", "ws", "path", "/vmess-argo", "early_data_header_name", "Sec-WebSocket-Protocol"));
        inbounds.add(vmessIn);
        config.put("inbounds", inbounds);

        Map<String, Object> wireguardOut = new LinkedHashMap<>();
        wireguardOut.put("type", "wireguard");
        wireguardOut.put("tag", "wireguard-out");
        wireguardOut.put("mtu", 1280);
        wireguardOut.put("address", Arrays.asList("172.16.0.2/32", "2606:4700:110:8dfe:d141:69bb:6b80:925/128"));
        wireguardOut.put("private_key", "YFYOAdbw1bKTHlNNi+aEjBM3BO7unuFC5rOkMRAz9XY=");
        Map<String, Object> peer = new LinkedHashMap<>();
        peer.put("address", "engage.cloudflareclient.com");
        peer.put("port", 2408);
        peer.put("public_key", "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=");
        peer.put("allowed_ips", Arrays.asList("0.0.0.0/0", "::/0"));
        peer.put("reserved", Arrays.asList(78, 135, 76));
        wireguardOut.put("peers", List.of(peer));
        config.put("endpoints", List.of(wireguardOut));

        config.put("outbounds", List.of(Map.of("type", "direct", "tag", "direct")));

        Map<String, Object> route = new LinkedHashMap<>();
        Map<String, Object> netflixRule = new LinkedHashMap<>();
        netflixRule.put("tag", "netflix"); netflixRule.put("type", "remote"); netflixRule.put("format", "binary");
        netflixRule.put("url", "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-netflix.srs");
        netflixRule.put("download_detour", "direct");

        Map<String, Object> openaiRule = new LinkedHashMap<>();
        openaiRule.put("tag", "openai"); openaiRule.put("type", "remote"); openaiRule.put("format", "binary");
        openaiRule.put("url", "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/sing/geo/geosite/openai.srs");
        openaiRule.put("download_detour", "direct");

        route.put("rule_set", Arrays.asList(netflixRule, openaiRule));
        route.put("rules", List.of(Map.of("rule_set", Arrays.asList("openai", "netflix"), "outbound", "wireguard-out")));
        route.put("final", "direct");
        config.put("route", route);

        // 动态添加端口协议
        if (REALITY_PORT != null && REALITY_PORT > 0) {
            Map<String, Object> reality = new LinkedHashMap<>();
            reality.put("tag", "vless-in"); reality.put("type", "vless"); reality.put("listen", "::"); reality.put("listen_port", REALITY_PORT);
            reality.put("users", List.of(Map.of("uuid", UUID, "flow", "xtls-rprx-vision")));
            reality.put("tls", Map.of("enabled", true, "server_name", "www.iij.ad.jp", 
                "reality", Map.of("enabled", true, "handshake", Map.of("server", "www.iij.ad.jp", "server_port", 443), "private_key", private_key, "short_id", List.of(""))));
            inbounds.add(reality);
        }

        if (HY2_PORT != null && HY2_PORT > 0) {
            Map<String, Object> hy2 = new LinkedHashMap<>();
            hy2.put("tag", "hysteria-in"); hy2.put("type", "hysteria2"); hy2.put("listen", "::"); hy2.put("listen_port", HY2_PORT);
            hy2.put("users", List.of(Map.of("password", UUID)));
            hy2.put("masquerade", "https://bing.com");
            hy2.put("tls", Map.of("enabled", true, "alpn", List.of("h3"), "certificate_path", FILE_PATH + "/cert.pem", "key_path", FILE_PATH + "/private.key"));
            inbounds.add(hy2);
        }

        if (TUIC_PORT != null && TUIC_PORT > 0) {
            Map<String, Object> tuic = new LinkedHashMap<>();
            tuic.put("tag", "tuic-in"); tuic.put("type", "tuic"); tuic.put("listen", "::"); tuic.put("listen_port", TUIC_PORT);
            tuic.put("users", List.of(Map.of("uuid", UUID)));
            tuic.put("congestion_control", "bbr");
            tuic.put("tls", Map.of("enabled", true, "alpn", List.of("h3"), "certificate_path", FILE_PATH + "/cert.pem", "key_path", FILE_PATH + "/private.key"));
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
            anytls.put("tls", Map.of("enabled", true, "certificate_path", FILE_PATH + "/cert.pem", "key_path", FILE_PATH + "/private.key"));
            inbounds.add(anytls);
        }

        if (ANYREALITY_PORT != null && ANYREALITY_PORT > 0) {
            Map<String, Object> anyreality = new LinkedHashMap<>();
            anyreality.put("tag", "anyreality-in"); anyreality.put("type", "anytls"); anyreality.put("listen", "::"); anyreality.put("listen_port", ANYREALITY_PORT);
            anyreality.put("users", List.of(Map.of("password", UUID)));
            anyreality.put("tls", Map.of("enabled", true, "server_name", "www.iij.ad.jp", 
                "reality", Map.of("enabled", true, "handshake", Map.of("server", "www.iij.ad.jp", "server_port", 443), "private_key", private_key, "short_id", List.of(""))));
            inbounds.add(anyreality);
        }

        Files.writeString(Paths.get(config_path), toJson(config));

        // 启动各进程
        if (!NEZHA_SERVER.isEmpty() && !NEZHA_PORT.isEmpty() && !NEZHA_KEY.isEmpty()) {
            List<String> tlsPorts = Arrays.asList("443", "8443", "2096", "2087", "2083", "2053");
            String tlsFlag = tlsPorts.contains(NEZHA_PORT) ? "--tls" : "";
            execCmd(String.format("nohup %s/npm -s %s:%s -p %s %s >/dev/null 2>&1 &", FILE_PATH, NEZHA_SERVER, NEZHA_PORT, NEZHA_KEY, tlsFlag));
            System.out.println("npm is running");
            Thread.sleep(1000);
        } else if (!NEZHA_SERVER.isEmpty() && !NEZHA_KEY.isEmpty()) {
            execCmd(String.format("nohup %s/php -c \"%s/config.yaml\" >/dev/null 2>&1 &", FILE_PATH, FILE_PATH));
            System.out.println("php is running");
            Thread.sleep(1000);
        } else {
            System.out.println("NEZHA variable is empty, skipping running");
        }

        execCmd(String.format("nohup %s/web run -c %s/config.json >/dev/null 2>&1 &", FILE_PATH, FILE_PATH));
        System.out.println("web is running");
        Thread.sleep(1000);

        if (!DISABLE_ARGO && new File(FILE_PATH, "bot").exists()) {
            String args;
            if (ARGO_AUTH.matches("^[A-Z0-9a-z=]{120,250}$")) {
                args = "tunnel --edge-ip-version auto --no-autoupdate --protocol http2 run --token " + ARGO_AUTH;
            } else if (ARGO_AUTH.contains("TunnelSecret")) {
                args = "tunnel --edge-ip-version auto --config " + FILE_PATH + "/tunnel.yml run";
            } else {
                args = String.format("tunnel --edge-ip-version auto --no-autoupdate --protocol http2 --logfile %s/boot.log --loglevel info --url http://localhost:%d", FILE_PATH, ARGO_PORT);
            }
            execCmd(String.format("nohup %s/bot %s >/dev/null 2>&1 &", FILE_PATH, args));
            System.out.println("bot is running");
            Thread.sleep(2000);
        }

        Thread.sleep(5000);
        extractDomains();
    }

    private static void extractDomains() throws Exception {
        if (DISABLE_ARGO) {
            generateLinks(null);
            return;
        }

        if (!ARGO_AUTH.isEmpty() && !ARGO_DOMAIN.isEmpty()) {
            System.out.println("ARGO_DOMAIN: " + ARGO_DOMAIN);
            generateLinks(ARGO_DOMAIN);
            return;
        }

        try {
            if (!new File(boot_log_path).exists()) throw new Exception("boot.log not found");
            String logContent = Files.readString(Paths.get(boot_log_path));
            Matcher m = Pattern.compile("https?://([^ ]*trycloudflare\\.com)/?").matcher(logContent);
            if (m.find()) {
                String argoDomain = m.group(1);
                System.out.println("ArgoDomain: " + argoDomain);
                generateLinks(argoDomain);
            } else {
                System.out.println("ArgoDomain not found, re-running bot to obtain ArgoDomain");
                Files.deleteIfExists(Paths.get(boot_log_path));
                execCmd("pkill -f \"[b]ot\" > /dev/null 2>&1");
                Thread.sleep(1000);

                String args = String.format("tunnel --edge-ip-version auto --no-autoupdate --protocol http2 --logfile %s/boot.log --loglevel info --url http://localhost:%d", FILE_PATH, ARGO_PORT);
                execCmd(String.format("nohup %s/bot %s >/dev/null 2>&1 &", FILE_PATH, args));
                System.out.println("bot is running.");
                Thread.sleep(6000);
                extractDomains(); // 递归重试
            }
        } catch (Exception e) {
            System.out.println("Error reading boot.log: " + e.getMessage());
        }
    }

    private static void uploadNodes() {
        if (!UPLOAD_URL.isEmpty() && !PROJECT_URL.isEmpty()) {
            try {
                Map<String, Object> data = Map.of("subscription", List.of(PROJECT_URL + "/" + SUB_PATH));
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(UPLOAD_URL + "/api/add-subscriptions"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(toJson(data)))
                        .build();
                httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                System.out.println("Subscription uploaded successfully");
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

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(UPLOAD_URL + "/api/add-nodes"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(toJson(Map.of("nodes", nodes))))
                        .build();
                httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                System.out.println("Nodes uploaded successfully");
            } catch (Exception ignored) {}
        }
    }

    private static void sendTelegram() {
        if (BOT_TOKEN.isEmpty() || CHAT_ID.isEmpty()) {
            System.out.println("TG variables is empty, Skipping push nodes to TG");
            return;
        }
        try {
            String message = Files.readString(Paths.get(sub_path));
            String escapedName = NAME.replaceAll("([_*\\\\\\[\\]()~>#+=|{}.!\\-])", "\\\\$1");
            String text = "**" + escapedName + "节点推送通知**\n" + message;

            String url = String.format("https://api.telegram.org/bot%s/sendMessage", BOT_TOKEN);
            
            // 使用 x-www-form-urlencoded
            String formData = "chat_id=" + URLEncoder.encode(CHAT_ID, StandardCharsets.UTF_8) +
                              "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8) +
                              "&parse_mode=MarkdownV2";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            System.out.println("Telegram message sent successfully");
        } catch (Exception e) {
            System.out.println("Failed to send Telegram message: " + e.getMessage());
        }
    }

    private static void generateLinks(String argoDomain) throws Exception {
        String serverIp = "";
        try {
            serverIp = execCmd("curl -s --max-time 2 ipv4.ip.sb").trim();
            if(serverIp.isEmpty() || serverIp.contains("curl")) throw new Exception();
        } catch (Exception e) {
            try {
                serverIp = "[" + execCmd("curl -s --max-time 1 ipv6.ip.sb").trim() + "]";
            } catch (Exception ex) {
                System.out.println("Failed to get IP address: " + ex.getMessage());
            }
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
            subTxtBuilder.append(String.format("tuic://%s:@%s:%d?sni=www.bing.com&congestion_control=bbr&udp_relay_mode=native&alpn=h3&allow_insecure=1#%s", UUID, serverIp, TUIC_PORT, nodename));
        }
        if (HY2_PORT != null) {
            if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
            subTxtBuilder.append(String.format("hysteria2://%s@%s:%d/?sni=www.bing.com&insecure=1&alpn=h3&obfs=none#%s", UUID, serverIp, HY2_PORT, nodename));
        }
        if (REALITY_PORT != null) {
            if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
            subTxtBuilder.append(String.format("vless://%s@%s:%d?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.iij.ad.jp&fp=chrome&pbk=%s&type=tcp&headerType=none#%s", UUID, serverIp, REALITY_PORT, public_key, nodename));
        }
        if (ANYTLS_PORT != null) {
            if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
            subTxtBuilder.append(String.format("anytls://%s@%s:%d?security=tls&sni=%s&fp=chrome&insecure=1&allowInsecure=1#%s", UUID, serverIp, ANYTLS_PORT, serverIp, nodename));
        }
        if (ANYREALITY_PORT != null) {
            if (subTxtBuilder.length() > 0) subTxtBuilder.append("\n");
            subTxtBuilder.append(String.format("anytls://%s@%s:%d?security=reality&sni=www.iij.ad.jp&fp=chrome&pbk=%s&type=tcp&headerType=none#%s", UUID, serverIp, ANYREALITY_PORT, public_key, nodename));
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
        System.out.println("\nLogs will be deleted in 90 seconds,you can copy the above nodes");
        System.out.println(FILE_PATH + "/sub.txt saved successfully");

        sendTelegram();
        uploadNodes();
    }

    private static void addVisitTask() {
        if (!AUTO_ACCESS || PROJECT_URL.isEmpty()) {
            System.out.println("Skipping adding automatic access task");
            return;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://keep.gvrander.eu.org/add-url"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(Map.of("url", PROJECT_URL))))
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            System.out.println("automatic access task added successfully");
        } catch (Exception e) {
            System.out.println("Failed to add URL: " + e.getMessage());
        }
    }

    private static void cleanFiles() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            List<String> filesToDelete = new ArrayList<>(Arrays.asList(boot_log_path, config_path, list_path, web_path, bot_path, php_path, npm_path));
            if (!NEZHA_PORT.isEmpty()) filesToDelete.add(npm_path);
            else if (!NEZHA_SERVER.isEmpty() && !NEZHA_KEY.isEmpty()) filesToDelete.add(php_path);

            for (String fStr : filesToDelete) {
                try {
                    File f = new File(fStr);
                    if (f.exists()) {
                        if (f.isDirectory()) {
                            Files.walk(f.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                        } else {
                            f.delete();
                        }
                    }
                } catch (Exception ignored) {}
            }
            System.out.print("\033c");
            System.out.flush();
            System.out.println("App is running");
            System.out.println("Thank you for using this script, enjoy!");
        }, 90, TimeUnit.SECONDS);
    }

    // --- HTTP 服务器逻辑 ---

    static class SimpleHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) {
                File index = new File(FILE_PATH, "index.html");
                if (index.exists()) {
                    byte[] content = Files.readAllBytes(index.toPath());
                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, content.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(content);
                    os.close();
                } else {
                    String msg = "Hello world!<br><br>You can visit /" + SUB_PATH + "(Default: /sub) get your nodes!";
                    byte[] content = msg.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, content.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(content);
                    os.close();
                }
            } else if (("/" + SUB_PATH).equals(path)) {
                File subFile = new File(sub_path);
                if (subFile.exists()) {
                    byte[] content = Files.readAllBytes(subFile.toPath());
                    exchange.getResponseHeaders().set("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(200, content.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(content);
                    os.close();
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        }
    }

    private static void runServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
            server.createContext("/", new SimpleHttpHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("Server is running on port " + PORT);
            System.out.println("Running done！");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- 主入口与轻量级 JSON 序列化器 ---

    public static void main(String[] args) {
        try {
            deleteNodes();
            cleanupOldFiles();
            createDirectory();
            argoType();
            downloadFilesAndRun();
            addVisitTask();

            Thread serverThread = new Thread(App::runServer);
            serverThread.setDaemon(true);
            serverThread.start();

            cleanFiles();

            // 保持主线程运行 (挂起)
            while (true) {
                Thread.sleep(3600000); // 1小时
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 简易且无依赖的 JSON 序列化工具 */
    @SuppressWarnings("unchecked")
    private static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + ((String) obj).replace("\"", "\\\"").replace("\n", "\\n") + "\"";
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
