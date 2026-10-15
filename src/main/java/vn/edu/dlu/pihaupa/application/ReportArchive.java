package vn.edu.dlu.pihaupa.application;

import piHAUPA.*;
import vn.edu.dlu.pihaupa.support.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Export adapter: uses completed results only; never re-runs mining or tests. */
public final class ReportArchive {
    private ReportArchive() {}
    /**
     * DESIGN PATTERN — FACADE xuất báo cáo; đồng thời chuyển định dạng đầu ra.
     * Phối hợp writer, HTML/SVG, measurement và ZIP từ job đã hoàn tất.
     * Không mining lại khi xuất: tránh công việc tính toán thừa.
     * Nội dung file/ZIP được tạo trong bộ nhớ, nên vẫn tiêu thụ heap theo kích thước kết quả.
     */
    public static Map<String,String> analysis(AnalysisService.Job job) {
        AnalysisReport r=job.report;
        Map<String,String> files=new LinkedHashMap<>();
        files.put("BAO_CAO.txt",ExportBundle.reportText(job.external,r,job.source));
        files.put("bao-cao.csv",ExportBundle.reportCsv(job.external,r,job.source));
        files.put("patterns.csv",ReportWriter.patternsCsv(r));
        files.put("performance-by-rows.csv",ReportWriter.performanceCsv(r));
        files.put("upper-bounds.csv",ExportBundle.upperBoundsCsv(r));
        files.put("report.json",job.result);
        files.put("measurements.csv",job.measurementCsv);
        files.put("resources.json",Json.encode(job.measurements.get("resources")));
        StringBuilder resourceCsv=new StringBuilder("elapsedMs,heapUsedMiB,heapCommittedMiB,processResidentMiB,systemUsedMiB,systemTotalMiB\n");
        if(job.measurements.get("resources") instanceof Map<?,?> resources && resources.get("samples") instanceof List<?> samplesList)
            for(Object entry:samplesList)if(entry instanceof Map<?,?> sample){
                for(String key:List.of("elapsedMs","heapUsedMiB","heapCommittedMiB","processResidentMiB","systemUsedMiB","systemTotalMiB"))resourceCsv.append(sample.get(key)==null?"":sample.get(key)).append(',');
                resourceCsv.setLength(resourceCsv.length()-1);resourceCsv.append('\n');
            }
        files.put("resource-samples.csv",resourceCsv.toString());
        files.put("run-measurements.json",Json.encode(job.measurements.get("runMeasurements")));
        StringBuilder perRun=new StringBuilder("run,runtimeMs,heapPeakMiB\n");
        if(job.measurements.get("runMeasurements") instanceof List<?> measuredRuns){int index=0;for(Object item:measuredRuns)if(item instanceof Map<?,?> run)perRun.append(++index).append(',').append(run.get("runtimeMs")).append(',').append(run.get("heapPeakMiB")).append('\n');}
        files.put("run-measurements.csv",perRun.toString());


        files.put("manifest.json",Json.encode(job.manifest));files.put("verification.json",Json.encode(job.oracle));
        StringBuilder samples=new StringBuilder("run,algorithmMs\n");for(int i=0;i<job.timingSamples.size();i++)samples.append(i+1).append(',').append(job.timingSamples.get(i)).append('\n');files.put("timing-samples.csv",samples.toString());
        if(job.isolatedComparisonCsv!=null)files.put("comparison.csv",job.isolatedComparisonCsv);
        if(job.comparison!=null){files.put("comparison.csv",ReportWriter.comparisonCsv(job.comparison));}
        StringBuilder html=new StringBuilder(head("Báo cáo PIHAUPA · "+job.source));
        html.append("<p>Sᵤ = ").append(r.upperThreshold()).append(" · Sₗ = ").append(r.lowerThreshold()).append(" · ").append(h(job.createdAt)).append("</p>");
        html.append("<p>01 Đọc & kiểm tra → 02 TU/MU & ngưỡng → 03 Quyết định re-scan → 04 Khai phá/cập nhật → 05 AU & phân loại → 06 Đo lường & xuất.</p>");
        var finalBatch=r.batches().get(r.batches().size()-1);
        html.append("<h2>Kết quả cuối · ").append(h(finalBatch.name())).append("</h2><p><strong>").append(finalBatch.largePatterns().size()).append(" HAUP (LARGE)</strong> · ").append(r.transactionCount()).append(" giao dịch · ").append(r.stats().rescanCount()).append(" re-scan, không gồm DB0.</p>");
        html.append("<p>AU(P)=U(P)/|P| ≥ Su×TU(DB) = ").append(finalBatch.minUtilUpper()).append(". PRE-LARGE không tính vào HAUP.</p><table><tr><th>Mẫu P</th><th>U(P)</th><th>|P|</th><th>AU(P)</th></tr>");
        for(var p:finalBatch.largePatterns().stream().sorted(Comparator.comparingDouble(PatternResult::averageUtility).reversed()).limit(20).toList())html.append("<tr><td>").append(h(p.displayName())).append("</td><td>").append(p.sumUtility()).append("</td><td>").append(p.length()).append("</td><td>").append(p.averageUtility()).append("</td></tr>");
        html.append("</table><p>Tối đa 20 HAUP; toàn bộ mẫu trong patterns.csv. Oracle độc lập: ").append(h(job.oracle.get("state"))).append(".</p>");
        html.append("<details><summary>Hồ sơ thực nghiệm</summary><p>SHA-256: ").append(h(job.manifest.get("sha256"))).append("</p><p>Java ").append(h(job.manifest.get("javaVersion"))).append(" · heap tối đa ").append(job.manifest.get("maxHeapMiB")).append(" MiB · ").append(job.timingSamples.size()).append(" lượt đo.</p><p>Chi tiết trong manifest.json, timing-samples.csv và verification.json.</p></details>");
        var labels=r.batches().stream().map(AnalysisReport.BatchView::name).toList();
        html.append(plot("02 · TU tích lũy (utility)",labels,r.batches().stream().map(b->b.totalTransactionUtility()).toList()));
        html.append(plot("05 · HAUP (mẫu)",labels,r.batches().stream().map(b->(double)b.largePatterns().size()).toList()));
        html.append(plot("06 · Thuật toán theo batch (ms)",labels,r.batches().stream().map(b->b.elapsedMs()).toList()));
        html.append(plot("06 · Đỉnh heap quan sát tích lũy (MiB, không phải RSS)",labels,r.batches().stream().map(b->b.peakMemoryMb()).toList()));
        html.append(plot("03 · Vế trái Eq. (12) (utility)",labels.subList(1,labels.size()),r.batches().stream().skip(1).map(b->b.accumulatedMaximumUtility()-r.upperThreshold()*b.accumulatedTransactionUtility()).toList()));
        html.append(plot("03 · Vế phải Eq. (12) (utility)",labels.subList(1,labels.size()),r.batches().stream().skip(1).map(b->(r.upperThreshold()-r.lowerThreshold())*(b.totalTransactionUtility()-b.accumulatedTransactionUtility())).toList()));
        html.append(plot("02 · Ngưỡng LARGE (utility)",labels,r.batches().stream().map(b->b.minUtilUpper()).toList()));
        html.append(plot("02 · Ngưỡng PRE-LARGE (utility)",labels,r.batches().stream().map(b->b.minUtilLower()).toList()));
        html.append(plot("05 · PRE-LARGE đang quản lý (mẫu)",labels,r.batches().stream().map(b->(double)b.preLargePatterns().size()).toList()));
        var last=r.batches().get(r.batches().size()-1);
        var top=last.largePatterns().stream().sorted(Comparator.comparingDouble(PatternResult::averageUtility).reversed()).limit(12).toList();
        html.append(plot("05 · Tối đa 12 HAUP có AU cao nhất (utility)",top.stream().map(PatternResult::displayName).toList(),top.stream().map(PatternResult::averageUtility).toList()));
        List<String> phaseNames=new ArrayList<>(List.of("Đọc + SHA-256","Thuật toán · median"));List<Double> phaseValues=new ArrayList<>(List.of(((Number)job.measurements.get("importMs")).doubleValue(),r.elapsedNanos()/1e6));
        if(job.measurements.get("threadCpuMs") instanceof Number cpu){phaseNames.add("CPU luồng · tổng các lượt");phaseValues.add(cpu.doubleValue());}
        html.append(plot("06 · Thời gian thực đo (ms)",phaseNames,phaseValues));
        html.append(plot("06 · Công việc miner (lượt / nút)",List.of("Mẫu duyệt","Nút kết hợp"),List.of((double)r.stats().patternsVisited(),(double)r.stats().combinedNodes())));
        html.append("<details><summary>Chi tiết từng bước và căn cứ</summary><pre>").append(h(files.get("BAO_CAO.txt"))).append("</pre></details>");
        if(job.measurements.get("resources") instanceof Map<?,?> resources && resources.get("samples") instanceof List<?> list){
            html.append("<h2>Số đo RAM (MiB)</h2><p>Đỉnh heap: ").append(h(resources.get("heapUsedMiBPeak"))).append(" · Đỉnh RAM tiến trình: ").append(h(resources.get("processResidentMiBPeak"))).append(" · Đỉnh RAM toàn máy dùng: ").append(h(resources.get("systemUsedMiBPeak"))).append("</p>");
            html.append("<table><tr><th>Thời điểm (ms)</th><th>Heap (MiB)</th><th>RAM Java (MiB)</th><th>RAM máy dùng (MiB)</th><th>Tổng RAM máy (MiB)</th></tr>");
            for(Object entry:list)if(entry instanceof Map<?,?> row){html.append("<tr>");for(String key:List.of("elapsedMs","heapUsedMiB","processResidentMiB","systemUsedMiB","systemTotalMiB"))html.append("<td>").append(row.get(key)==null?"Chưa đo":h(row.get(key))).append("</td>");html.append("</tr>");}
            html.append("</table><p>Lấy mẫu khoảng 1 giây; không gồm RAM JVM con; RAM toàn máy gồm mọi ứng dụng. Chi tiết trong resource-samples.csv.</p>");
        }
        html.append("<h2>Đo lường của lượt chạy</h2><pre>").append(h(job.measurementCsv)).append("</pre>");
        if(job.comparison!=null||job.isolatedComparisonCsv!=null)html.append("<h2>Đối chiếu Original / Tight</h2><pre>").append(h(files.get("comparison.csv"))).append("</pre>");
        files.put("BAO_CAO.html",html.append("</body></html>").toString());
        files.put("README.txt","Mở BAO_CAO.html để xem biểu đồ offline. BAO_CAO.txt và bao-cao.csv: phép tính đánh dấu từng bước. patterns.csv: toàn bộ mẫu quản lý. performance-by-rows.csv: số đo và quyết định từng batch. upper-bounds.csv: AUUB. measurements.csv: đọc, thuật toán, CPU, heap. resource-samples.csv/resources.json: heap, RAM tiến trình, RAM toàn máy theo thời gian. run-measurements.json/csv: runtime/heap từng lượt đo. manifest.json: SHA-256, JVM và cấu hình; verification.json: oracle; timing-samples.csv: từng lượt đo. report.json: toàn bộ kết quả, preview tối đa 30 giao dịch và comparison nếu có. comparison.csv chỉ có nếu đã bật đối chiếu. Không đóng gói tệp giao dịch lớn gốc; không tái chạy thuật toán khi xuất.\nHeap là đỉnh quan sát của JVM chung, không phải RSS; thời gian kiểm thử không phải benchmark bài báo.\n");
        return files;
    }
    public static Map<String,String> tests(Map<String,Object> result){
        @SuppressWarnings("unchecked") var cases=(List<Map<String,Object>>)result.get("cases");
        StringBuilder csv=new StringBuilder("id,title,input,expected,actual,state,elapsedMs,detail,heapBeforeMiB,heapAfterMiB,systemUsedAfterMiB\n");
        for(var c:cases){for(String key:List.of("id","title","input","expected","actual","state","elapsedMs","detail"))csv.append(q(c.getOrDefault(key,""))).append(',');if(c.get("memoryBefore") instanceof Map<?,?> before)csv.append(before.get("heapUsedMiB"));csv.append(',');if(c.get("memoryAfter") instanceof Map<?,?> after)csv.append(after.get("heapUsedMiB")).append(',').append(after.get("systemUsedMiB"));else csv.append(',');csv.append('\n');}
        StringBuilder html=new StringBuilder(head("Test case & đối chiếu bài báo"));
        html.append("<p>TC-P01–12: kiểm chứng ví dụ Tables 2–3, Table 5 và Eq. (5),(6),(12). TC-E/A/D: kiểm thử bổ sung của ứng dụng. Không tái lập toàn bộ benchmark trong bài báo.</p><p>01 Dữ liệu & oracle cố định → 02 Chạy Java → 03 So sánh kỳ vọng/thực tế → 04 Ghi ĐẠT/KHÔNG ĐẠT → 05 Xuất bằng chứng.</p>");
        html.append("<p>Trạng thái: ").append(h(result.get("state"))).append(" · thời điểm: ").append(h(result.getOrDefault("runAt","Chưa chạy"))).append("</p>");
        html.append(plot("Kết quả theo nhóm (case đạt)",List.of("Bài báo","Lỗi","Thuật toán","Parser"),List.of(count(cases,"TC-P"),count(cases,"TC-E"),count(cases,"TC-A"),count(cases,"TC-D"))));
        if(!"NOT_RUN".equals(result.get("state"))){
            html.append(plot("Thời gian case (ms); TC-P dùng chung lượt chạy mẫu, không phải benchmark",cases.stream().map(c->c.get("id").toString()).toList(),cases.stream().map(c->((Number)c.get("elapsedMs")).doubleValue()).toList()));
            var numeric=cases.stream().filter(c->c.get("id").toString().startsWith("TC-P")&&!c.get("expected").toString().startsWith("{")).toList();
            for(var c:numeric)html.append(plot(c.get("id")+" · "+c.get("title"),numericLabels(c.get("actual")),numericValues(c.get("expected"),c.get("actual"))));
        }
        for(var c:cases){if(!c.get("expected").toString().startsWith("{"))continue;
            var expected=patternValues(c.get("expected"));var actual=patternValues(c.get("actual"));
            html.append(plot(c.get("id")+" · AU kỳ vọng (utility)",new ArrayList<>(expected.keySet()),new ArrayList<>(expected.values())));
            if(!actual.isEmpty())html.append(plot(c.get("id")+" · AU Java (utility)",new ArrayList<>(actual.keySet()),new ArrayList<>(actual.values())));
        }
        html.append("<table><tr><th>Case</th><th>Đầu vào</th><th>Kỳ vọng</th><th>Thực tế</th><th>Trạng thái</th><th>ms</th></tr>");
        for(var c:cases){html.append("<tr>");for(String key:List.of("id","input","expected","actual","state","elapsedMs"))html.append("<td>").append(h(c.getOrDefault(key,"Chưa chạy"))).append("</td>");html.append("</tr>");}
        return Map.of("testcases.csv",csv.toString(),"testcases.json",Json.encode(result),"TESTCASES.html",html.append("</table></body></html>").toString());
    }
    private static Map<String,Double> patternValues(Object value){
        Map<String,Double> values=new LinkedHashMap<>();
        var matcher=java.util.regex.Pattern.compile("(?:\\{|, )([^=]+)=([0-9.]+)").matcher(String.valueOf(value));
        while(matcher.find())values.put(matcher.group(1),Double.parseDouble(matcher.group(2)));
        return values;
    }
    private static List<String> numericLabels(Object actual){try{Double.parseDouble(String.valueOf(actual));return List.of("Kỳ vọng","Java");}catch(NumberFormatException e){return List.of("Kỳ vọng · Java lỗi, xem bảng");}}
    private static List<Double> numericValues(Object expected,Object actual){double e=Double.parseDouble(String.valueOf(expected));try{return List.of(e,Double.parseDouble(String.valueOf(actual)));}catch(NumberFormatException failure){return List.of(e);}}
    private static double count(List<Map<String,Object>> cases,String prefix){return cases.stream().filter(c->c.get("id").toString().startsWith(prefix)&&"PASSED".equals(c.get("state"))).count();}
    public static byte[] zip(Map<String,String> files)throws IOException{var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)){for(var entry:files.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}return bytes.toByteArray();}
    private static String head(String title){return "<!doctype html><html lang='vi'><meta charset='utf-8'><title>"+h(title)+"</title><style>body{font:15px system-ui;color:#17304b;background:#f5f8fc;max-width:1100px;margin:40px auto;padding:24px}section,pre,table{background:white;padding:20px;border:1px solid #dce5ef;border-radius:12px}pre{white-space:pre-wrap}table{width:100%;border-collapse:collapse}th{text-align:right}td{text-align:right}td:first-child,th:first-child{text-align:left}td,th{border-bottom:1px solid #ddd;padding:10px;overflow-wrap:anywhere}svg{width:100%;height:auto}details{margin:16px 0;padding:16px;background:white;border:1px solid #dce5ef;border-radius:10px}summary{cursor:pointer;font-weight:600}section{margin:16px 0}</style><body><h1>"+h(title)+"</h1>";}
    private static String plot(String title,List<String> labels,List<Double> values){double max=values.stream().mapToDouble(Math::abs).max().orElse(1);if(max==0)max=1;StringBuilder out=new StringBuilder("<section><h2>"+h(title)+"</h2><p><strong>X:</strong> Giá trị (đơn vị trong tiêu đề) · <strong>Y:</strong> Mốc / hạng mục ghi cạnh thanh</p><svg role='img' aria-label='"+h(title)+"' viewBox='0 0 900 "+Math.max(50,labels.size()*38)+"'>");for(int i=0;i<labels.size();i++){int y=i*38;out.append("<text x='0' y='").append(y+22).append("'>").append(h(labels.get(i))).append("</text><rect x='170' y='").append(y+4).append("' width='").append(Math.abs(values.get(i))/max*500).append("' height='25' fill='#2463ed'/><text x='680' y='").append(y+22).append("'>").append(values.get(i)).append("</text>");}return out.append("</svg><p>Độ dài thanh biểu diễn độ lớn; số bên cạnh giữ nguyên dấu.</p></section>").toString();}
    private static String q(Object s){return "\""+String.valueOf(s).replace("\"","\"\"")+"\"";}
    private static String h(Object s){return String.valueOf(s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
