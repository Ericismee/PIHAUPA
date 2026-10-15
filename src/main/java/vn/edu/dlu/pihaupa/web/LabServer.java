package vn.edu.dlu.pihaupa.web;

import com.sun.net.httpserver.*;
import vn.edu.dlu.pihaupa.application.*;
import piHAUPA.ExportBundle;
import vn.edu.dlu.pihaupa.support.Json;
import vn.edu.dlu.pihaupa.support.AppError;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;

/** HTTP adapter/controller: no mining logic lives in this layer. Localhost only. */
/**
 * DESIGN PATTERN — ADAPTER tại ranh giới HTTP (vai trò kiến trúc).
 * Chuyển method/query/body thành lời gọi ứng dụng và chuyển kết quả thành HTTP/JSON/file.
 * Công thức AU, MAU và re-scan nằm trong lõi Java, không tính lại trong controller.
 * Lợi ích: tách giao thức khỏi khai phá; không phải GoF Object Adapter đầy đủ.
 */
public final class LabServer {
    private final AnalysisService service = new AnalysisService();
    private final DatasetCatalog catalog;
    private LabServer(DatasetCatalog catalog) { this.catalog=catalog; }
    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 8000 : Integer.parseInt(args[0]);
        String root = System.getenv("PIHAUPA_DATA_ROOT");
        DatasetCatalog catalog = new DatasetCatalog(root == null || root.isBlank() ? null : Path.of(root));
        LabServer app = new LabServer(catalog);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        server.createContext("/api/",app::api);
        server.createContext("/",app::staticFile);
        var httpWorkers = Executors.newFixedThreadPool(4);
        server.setExecutor(httpWorkers);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.stop(0); app.service.close(); httpWorkers.shutdownNow(); }));
        server.start();
        System.out.println("PIHAUPA Research Lab: http://localhost:"+server.getAddress().getPort());
    }
    private void api(HttpExchange x) throws IOException {
        try {
            // Block cross-origin browser requests to the localhost analysis service.
            String origin = x.getRequestHeaders().getFirst("Origin");
            String host = x.getRequestHeaders().getFirst("Host");
            if (origin != null && !origin.equals("http://"+host)) { send(x,403,"application/json",Json.encode(Map.of("error","Origin không hợp lệ.","code","ORIGIN_FORBIDDEN","action","Mở trang từ đúng địa chỉ localhost của server Java."))); return; }
            String path=x.getRequestURI().getPath(), method=x.getRequestMethod();
            Map<String,String> q=query(x.getRequestURI().getRawQuery());
            if (path.equals("/api/datasets") && method.equals("GET")) {
                send(x,200,"application/json",Json.encode(catalog.list()));
            } else if (path.equals("/api/verify") && method.equals("GET")) {
                send(x,200,"application/json",Json.encode(service.verifyPaper()));
            } else if(path.equals("/api/testcases") && method.equals("GET")) {
                send(x,200,"application/json",Json.encode(TestCaseRunner.snapshot()));
            } else if(path.equals("/api/testcases/export") && method.equals("GET")) {
                x.getResponseHeaders().set("Content-Disposition","attachment; filename=\"pihaupa-testcases.zip\"");
                sendBytes(x,"application/zip",ReportArchive.zip(ReportArchive.tests(TestCaseRunner.snapshot())));
            } else if(path.equals("/api/testcases/run") && method.equals("POST")) {
                send(x,200,"application/json",Json.encode(service.runTestCases()));
            } else if (path.equals("/api/jobs") && method.equals("POST")) {
                double su=Double.parseDouble(q.getOrDefault("su","0.23")), sl=Double.parseDouble(q.getOrDefault("sl","0.10"));
                if (!(sl>0 && sl<su && su<=1)) throw new IllegalArgumentException("Cần 0 < Sl < Su ≤ 1.");
                InputSource source;
                String kind=q.getOrDefault("kind","paper");
                if (kind.equals("paper")) source=InputSource.paper();
                else if (kind.equals("catalog")) source=InputSource.catalog(catalog,q.get("dataset"));
                else if (kind.equals("upload")) {
                    Path temporary=Files.createTempFile("pihaupa-", ".txt");
                    try (var out=Files.newOutputStream(temporary)) { x.getRequestBody().transferTo(out); }
                    catch (Exception e) { Files.deleteIfExists(temporary); throw e; }
                    source=InputSource.upload(temporary,q.getOrDefault("name","transactions.txt"), !"quantitative".equals(q.get("format")));
                } else throw new IllegalArgumentException("Nguồn dữ liệu không hợp lệ.");
                try {
                    source=InputSource.fraction(source,Integer.parseInt(q.getOrDefault("sizePercent","100")));
                    var job=service.submit(source,su,sl,Boolean.parseBoolean(q.getOrDefault("compare","false")),Integer.parseInt(q.getOrDefault("repeats","1")));
                    send(x,202,"application/json",job.status());
                } catch (RuntimeException e) { source.cleanup(); throw e; }
            } else if(path.startsWith("/api/jobs/") && path.endsWith("/cancel") && method.equals("POST")) {
                String id=path.substring("/api/jobs/".length(),path.length()-"/cancel".length());send(x,202,"application/json",service.cancel(id).status());
            } else if(path.equals("/api/runs") && method.equals("GET")) {send(x,200,"application/json",service.store().list());
            } else if(path.startsWith("/api/runs/") && method.equals("GET")) {
                String[] parts=path.substring("/api/runs/".length()).split("/");
                if(parts.length==1)send(x,200,"application/json",service.store().result(parts[0]));
                else if(parts.length==2&&parts[1].equals("export")){x.getResponseHeaders().set("Content-Disposition","attachment; filename=\"pihaupa-run.zip\"");sendBytes(x,"application/zip",service.store().archive(parts[0]));}
                else send(x,404,"application/json","{}");
            } else if (path.startsWith("/api/jobs/") && method.equals("GET")) {
                String[] parts=path.substring("/api/jobs/".length()).split("/");
                var job=service.get(parts[0]);
                if (parts.length==1) send(x,200,"application/json",job.status());
                else if (parts.length==2 && parts[1].equals("result")) {
                    if (!job.state.equals("COMPLETED")) throw new IllegalStateException("Báo cáo chưa sẵn sàng.");
                    send(x,200,"application/json",job.result);
                } else if (parts.length==2 && parts[1].equals("export")) {
                    if (!job.state.equals("COMPLETED")) throw new IllegalStateException("Báo cáo chưa sẵn sàng.");
                    String format=q.getOrDefault("format","csv");
                    if(format.equals("zip")) {
                        x.getResponseHeaders().set("Content-Disposition","attachment; filename=\"pihaupa-analysis.zip\"");
                        sendBytes(x,"application/zip",ReportArchive.zip(ReportArchive.analysis(job))); return;
                    }
                    if (!Set.of("csv","txt").contains(format)) throw new IllegalArgumentException("Chỉ hỗ trợ CSV/TXT.");
                    String content=format.equals("csv") ? ExportBundle.reportCsv(job.external,job.report,job.source)
                            : ExportBundle.reportText(job.external,job.report,job.source);
                    x.getResponseHeaders().set("Content-Disposition","attachment; filename=\"pihaupa-report."+format+"\"");
                    send(x,200,format.equals("csv")?"text/csv":"text/plain",content);
                } else send(x,404,"application/json","{\"error\":\"Không tìm thấy API.\"}");
            } else send(x,404,"application/json","{\"error\":\"Không tìm thấy API hoặc phương thức không hỗ trợ.\"}");
        } catch (NoSuchElementException e) { send(x,404,"application/json",Json.encode(AppError.from(e).toMap())); }
        catch (IllegalStateException e) { send(x,409,"application/json",Json.encode(AppError.from(e).toMap())); }
        catch (Exception e) { AppError error=AppError.from(e); send(x,Set.of("INTERNAL_ERROR","DATA_IO").contains(error.code())?500:400,"application/json",Json.encode(error.toMap())); }
    }
    private void staticFile(HttpExchange x) throws IOException {
        String path=x.getRequestURI().getPath();
        if (path.equals("/")) path="/index.html";
        if (!Set.of("/index.html","/styles.css","/app.js","/charts.js","/api.js","/ui-components.js","/report-ui.js","/notifications.js","/report.css").contains(path) || !x.getRequestMethod().equals("GET")) {
            send(x,404,"text/plain","Not found"); return;
        }
        try (InputStream in=LabServer.class.getResourceAsStream("/static"+path)) {
            if (in==null) { send(x,404,"text/plain","Resource missing"); return; }
            send(x,200,path.endsWith(".css")?"text/css":path.endsWith(".js")?"text/javascript":"text/html",new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    private static Map<String,String> query(String raw) {
        Map<String,String> q=new HashMap<>();
        if (raw!=null) for (String pair:raw.split("&")) {
            String[] kv=pair.split("=",2);
            q.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),kv.length>1?URLDecoder.decode(kv[1],StandardCharsets.UTF_8):"");
        }
        return q;
    }
    private static void sendBytes(HttpExchange x,String type,byte[] bytes)throws IOException {
        x.getResponseHeaders().set("Content-Type",type);
        x.getResponseHeaders().set("Cache-Control","no-store");
        x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        x.sendResponseHeaders(200,bytes.length);
        try(var out=x.getResponseBody()){out.write(bytes);}
    }
    private static void send(HttpExchange x,int status,String type,String content) throws IOException {
        byte[] bytes=content.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control","no-store");
        x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        x.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'");
        x.sendResponseHeaders(status,bytes.length);
        try (var out=x.getResponseBody()) { out.write(bytes); }
    }
}
