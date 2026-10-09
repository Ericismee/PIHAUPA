package piHAUPA;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public final class WebServer {
    private static final Path WEB_ROOT = Path.of("web").toAbsolutePath().normalize();
    private static final Map<String, ProgressLog> PROGRESS = new ConcurrentHashMap<>();
    private static final long EXPORT_TTL_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(30);
    private static final int MAX_CACHED_EXPORTS = 2;
    private static final LinkedHashMap<String, CachedAnalysis> EXPORTS = new LinkedHashMap<>();

    private record CachedAnalysis(Map<String, Double> externalUtilities, AnalysisReport report,
                                  String source, long createdAtNanos) { }

    private WebServer() {
    }

    public static void main(String[] args) throws Exception {
        int requestedPort = args.length == 0 ? 8000 : Integer.parseInt(args[0]);
        HttpServer server = bind(requestedPort);
        server.createContext("/api/analyze", WebServer::analyze);
        server.createContext("/api/export", WebServer::export);
        server.createContext("/api/archive/inspect", WebServer::inspectArchive);
        server.createContext("/api/progress", WebServer::progress);
        server.createContext("/", WebServer::serveStatic);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(2,
                Runtime.getRuntime().availableProcessors())));
        server.start();
        int port = server.getAddress().getPort();
        URI uri = URI.create("http://localhost:" + port + "/");
        System.out.println("PIHAUPA Web dang chay tai " + uri);
        System.out.println("Nhan Ctrl+C de dung.");
        boolean openBrowser = args.length < 2 || !"--no-browser".equalsIgnoreCase(args[1]);
        if (openBrowser && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
        }
    }

    private static HttpServer bind(int requestedPort) throws IOException {
        try {
            return HttpServer.create(new InetSocketAddress("127.0.0.1", requestedPort), 0);
        } catch (IOException error) {
            throw new IOException("Cổng " + requestedPort + " đang được sử dụng. "
                    + "Hãy đóng server PIHAUPA cũ hoặc chọn cổng khác khi chạy run-web.ps1.", error);
        }
    }

    private static void analyze(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json", "{\"error\":\"Chỉ hỗ trợ POST.\"}");
            return;
        }
        String job = parseQuery(exchange.getRequestURI().getRawQuery()).getOrDefault("job", "");
        ProgressLog log = startProgress(job);
        Consumer<String> progress = message -> logMessage(job, log, message);
        try {
            long requestStarted = System.nanoTime();
            long heapBefore = usedHeap();
            ThreadMXBean cpuBean = ManagementFactory.getThreadMXBean();
            long cpuBefore = cpuBean.isCurrentThreadCpuTimeSupported()
                    ? cpuBean.getCurrentThreadCpuTime() : -1L;
            InputData input = parseInput(exchange, progress);
            long imported = System.nanoTime();
            long heapAfterInput = usedHeap();
            progress.accept("Đã nạp dữ liệu; bắt đầu PIHAUPA Tight");
            AnalysisReport report = AnalysisReport.run(input, RescanPolicy.TIGHT, progress);
            long mined = System.nanoTime();
            long heapAfterMining = usedHeap();
            progress.accept("Hoàn tất PIHAUPA Tight");
            String reportJson = report.toJson();
            String previewJson = inputPreviewJson(input);
            long responseBuilt = System.nanoTime();
            long heapAfterResponse = usedHeap();
            long cpuAfter = cpuBefore < 0 ? -1L : cpuBean.getCurrentThreadCpuTime();
            double cpuMs = cpuBefore >= 0 && cpuAfter >= cpuBefore
                    ? (cpuAfter - cpuBefore) / 1_000_000.0 : -1.0;
            double heapPeak = Math.max(Math.max(heapBefore, heapAfterInput),
                    Math.max(Math.max(heapAfterMining, heapAfterResponse), report.peakMemoryBytes()));
            String measurement = String.format(java.util.Locale.US,
                    "{\"importMs\":%.6f,\"algorithmMs\":%.6f,\"responseBuildMs\":%.6f,"
                            + "\"requestMs\":%.6f,\"threadCpuMs\":%s,"
                            + "\"heapBeforeMb\":%.6f,\"heapAfterInputMb\":%.6f,"
                            + "\"heapAfterMiningMb\":%.6f,\"heapPeakObservedMb\":%.6f}",
                    (imported - requestStarted) / 1_000_000.0,
                    (mined - imported) / 1_000_000.0,
                    (responseBuilt - mined) / 1_000_000.0,
                    (responseBuilt - requestStarted) / 1_000_000.0,
                    cpuMs < 0 ? "null" : String.format(java.util.Locale.US, "%.6f", cpuMs),
                    heapBefore / 1048576.0, heapAfterInput / 1048576.0,
                    heapAfterMining / 1048576.0, heapPeak / 1048576.0);
            progress.accept(String.format(java.util.Locale.US,
                    "Đo lường: đọc TXT %.1f ms; thuật toán %.1f ms; CPU luồng %s; heap peak quan sát %.1f MB",
                    (imported - requestStarted) / 1_000_000.0,
                    report.elapsedNanos() / 1_000_000.0,
                    cpuMs < 0 ? "không khả dụng" : String.format(java.util.Locale.US, "%.1f ms", cpuMs),
                    heapPeak / 1048576.0));
            String source = parseQuery(exchange.getRequestURI().getRawQuery())
                    .getOrDefault("source", "Dữ liệu web");
            String exportId = cacheAnalysis(input.externalUtilities(), report, source);
            send(exchange, 200, "application/json; charset=utf-8",
                    reportJson.substring(0, reportJson.length() - 1)
                    + ",\"inputPreview\":" + previewJson + ",\"measurement\":" + measurement
                    + ",\"exportId\":\"" + exportId + "\"}");
        } catch (Exception error) {
            progress.accept("Lỗi: " + error.getMessage());
            sendError(exchange, error);
        } finally {
            if (log != null) log.done = true;
        }
    }

    private static void export(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
            send(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"Chỉ hỗ trợ GET hoặc POST.\"}");
            return;
        }
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String format = query.getOrDefault("format", "txt");
            if (!format.equals("txt") && !format.equals("csv")) {
                throw new IllegalArgumentException("Chỉ hỗ trợ xuất TXT hoặc CSV.");
            }
            String reportText;
            if ("GET".equalsIgnoreCase(method)) {
                CachedAnalysis cached = cachedAnalysis(query.getOrDefault("id", ""));
                if (cached == null) {
                    send(exchange, 410, "application/json; charset=utf-8",
                            "{\"error\":\"Bản phân tích đã hết hạn. Hãy bấm Chạy phân tích rồi xuất lại.\"}");
                    return;
                }
                reportText = format.equals("txt")
                        ? ExportBundle.reportText(cached.externalUtilities(), cached.report(), cached.source())
                        : ExportBundle.reportCsv(cached.externalUtilities(), cached.report(), cached.source());
            } else {
                // Tương thích với client cũ; giao diện mới dùng GET và không chạy thuật toán lần hai.
                InputData input = parseInput(exchange, ignored -> { });
                AnalysisReport report = AnalysisReport.run(input);
                String source = query.getOrDefault("source", "Dữ liệu web");
                reportText = format.equals("txt") ? ExportBundle.reportText(input, report, source)
                        : ExportBundle.reportCsv(input, report, source);
            }
            byte[] body = reportText.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type",
                    format.equals("txt") ? "text/plain; charset=utf-8" : "text/csv; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=PIHAUPA-bao-cao." + format);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        } catch (Exception error) {
            sendError(exchange, error);
        }
    }

    private static synchronized String cacheAnalysis(Map<String, Double> externalUtilities,
            AnalysisReport report, String source) {
        long now = System.nanoTime();
        EXPORTS.entrySet().removeIf(entry -> now - entry.getValue().createdAtNanos() > EXPORT_TTL_NANOS);
        while (EXPORTS.size() >= MAX_CACHED_EXPORTS) {
            EXPORTS.remove(EXPORTS.keySet().iterator().next());
        }
        String id = UUID.randomUUID().toString();
        // AnalysisReport và EU đủ để xuất; không giữ InputData/batches giao dịch cỡ lớn.
        EXPORTS.put(id, new CachedAnalysis(externalUtilities, report, source, now));
        return id;
    }

    private static synchronized CachedAnalysis cachedAnalysis(String id) {
        CachedAnalysis cached = EXPORTS.get(id);
        if (cached != null && System.nanoTime() - cached.createdAtNanos() > EXPORT_TTL_NANOS) {
            EXPORTS.remove(id);
            return null;
        }
        return cached;
    }

    private static void inspectArchive(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"Chỉ hỗ trợ POST.\"}");
            return;
        }
        String job = parseQuery(exchange.getRequestURI().getRawQuery()).getOrDefault("job", "");
        ProgressLog log = startProgress(job);
        try {
            if (log != null) log.add("Bắt đầu kiểm tra ZIP");
            List<ArchiveDatasetReader.DatasetAudit> audits = ArchiveDatasetReader.inspect(
                    exchange.getRequestBody(), message -> logMessage(job, log, message));
            StringBuilder json = new StringBuilder("{\"datasets\":[");
            for (int i = 0; i < audits.size(); i++) {
                if (i > 0) json.append(',');
                json.append(audits.get(i).toJson());
            }
            json.append("],\"utilityMapping\":\"EU=1; IU=utility đã ghi trong transactions.txt; "
                    + "investment_table.txt chỉ dùng để kiểm tra metadata\"}");
            if (log != null) log.add("Hoàn tất kiểm tra " + audits.size() + " bộ dữ liệu");
            send(exchange, 200, "application/json; charset=utf-8", json.toString());
        } catch (Exception error) {
            if (log != null) log.add("Lỗi: " + error.getMessage());
            sendError(exchange, error);
        } finally {
            if (log != null) log.done = true;
        }
    }

    private static void progress(HttpExchange exchange) throws IOException {
        String job = parseQuery(exchange.getRequestURI().getRawQuery()).getOrDefault("job", "");
        ProgressLog log = PROGRESS.get(job);
        if (log == null) {
            send(exchange, 200, "application/json; charset=utf-8", "{\"done\":false,\"lines\":[]}");
            return;
        }
        StringBuilder json = new StringBuilder("{\"done\":").append(log.done).append(",\"lines\":[");
        List<String> lines = log.snapshot();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) json.append(',');
            json.append('"').append(escape(lines.get(i))).append('"');
        }
        json.append("]}");
        send(exchange, 200, "application/json; charset=utf-8", json.toString());
    }

    private static InputData parseInput(HttpExchange exchange, Consumer<String> progress) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        double su = Double.parseDouble(query.getOrDefault("su", "0.23"));
        double sl = Double.parseDouble(query.getOrDefault("sl", "0.10"));
        if (!(sl > 0.0 && sl < su && su <= 1.0)) {
            throw new IllegalArgumentException("Cần 0 < Sl < Su ≤ 1.");
        }
        if (exchange.getRequestHeaders().getFirst("Content-Type") != null
                && exchange.getRequestHeaders().getFirst("Content-Type").startsWith("application/zip")) {
            return ArchiveDatasetReader.read(exchange.getRequestBody(), query.get("dataset"), su, sl, progress);
        }
        if ("transactions".equals(query.get("inputFormat"))) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                return ArchiveDatasetReader.readTransactionsText(reader,
                        query.getOrDefault("dataset", "transactions.txt"), su, sl, progress);
            }
        }
        InputData parsed;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            reader.mark(1);
            int firstCharacter = reader.read();
            if (firstCharacter == -1) {
                parsed = InputParser.parseBuiltInSample();
            } else {
                reader.reset();
                parsed = InputParser.parse(reader);
            }
        }
        return new InputData(su, sl, parsed.externalUtilities(), parsed.batches());
    }

    private static void sendError(HttpExchange exchange, Exception error) throws IOException {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        send(exchange, 400, "application/json; charset=utf-8",
                "{\"error\":\"" + escape(message) + "\"}");
    }

    private static void serveStatic(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        String relative = "/".equals(requestPath) ? "index.html" : requestPath.substring(1);
        if (!relative.equals("index.html") && !relative.equals("app.js") && !relative.equals("styles.css")) {
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        Path file = WEB_ROOT.resolve(relative).normalize();
        if (!file.startsWith(WEB_ROOT) || !Files.isRegularFile(file)) {
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        String contentType = relative.endsWith(".css") ? "text/css; charset=utf-8"
                : relative.endsWith(".js") ? "text/javascript; charset=utf-8"
                : "text/html; charset=utf-8";
        byte[] body = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String token : raw.split("&")) {
            String[] pair = token.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }

    private static void send(HttpExchange exchange, int status, String contentType, String text)
            throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static String inputPreviewJson(InputData input) {
        StringBuilder json = new StringBuilder("{\"itemCount\":")
                .append(input.externalUtilities().size()).append(",\"items\":[");
        int shown = 0;
        for (var entry : input.externalUtilities().entrySet()) {
            if (shown++ == 30) break;
            if (shown > 1) json.append(',');
            json.append("{\"name\":\"").append(escape(entry.getKey())).append("\",\"eu\":")
                    .append(entry.getValue()).append('}');
        }
        json.append("],\"transactions\":[");
        shown = 0;
        for (Batch batch : input.batches()) {
            for (Transaction transaction : batch.transactions()) {
                if (shown == 30) break;
                if (shown++ > 0) json.append(',');
                StringBuilder items = new StringBuilder();
                transaction.internalUtilities().forEach((item, iu) -> {
                    if (!items.isEmpty()) items.append(" · ");
                    items.append(item).append(':').append(iu);
                });
                json.append("{\"batch\":\"").append(escape(batch.name()))
                        .append("\",\"tid\":\"").append(escape(transaction.tid()))
                        .append("\",\"items\":\"").append(escape(items.toString()))
                        .append("\",\"tu\":").append(transaction.transactionUtility(input.externalUtilities()))
                        .append(",\"mu\":").append(transaction.maximumUtility(input.externalUtilities()))
                        .append('}');
            }
            if (shown == 30) break;
        }
        return json.append("]}").toString();
    }

    private static ProgressLog startProgress(String job) {
        if (!job.matches("[A-Za-z0-9-]{1,64}")) return null;
        if (PROGRESS.size() >= 16) PROGRESS.clear();
        ProgressLog log = new ProgressLog();
        PROGRESS.put(job, log);
        return log;
    }

    private static void logMessage(String job, ProgressLog log, String message) {
        System.out.println("[PIHAUPA " + job + "] " + message);
        if (log != null) log.add(message);
    }

    private static final class ProgressLog {
        private final ArrayDeque<String> lines = new ArrayDeque<>();
        private volatile boolean done;

        synchronized void add(String line) {
            if (lines.size() == 200) lines.removeFirst();
            lines.addLast(line);
        }

        synchronized List<String> snapshot() {
            return new ArrayList<>(lines);
        }
    }
}
