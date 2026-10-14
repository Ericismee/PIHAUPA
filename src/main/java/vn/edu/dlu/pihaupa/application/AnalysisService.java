package vn.edu.dlu.pihaupa.application;

import piHAUPA.*;
import vn.edu.dlu.pihaupa.support.Json;
import vn.edu.dlu.pihaupa.support.AppError;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Application facade. One mining worker isolates measurements and bounds heap pressure. */
public final class AnalysisService implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String, Job> jobs = new LinkedHashMap<>();
    private boolean active;
    public synchronized Job submit(InputSource source, double su, double sl, boolean compare) {
        if (active) throw new IllegalStateException("Một phép đo đang chạy. Hãy đợi hoàn tất.");
        if (!(sl > 0 && sl < su && su <= 1)) throw new IllegalArgumentException("Cần 0 < Sl < Su ≤ 1.");
        while (jobs.size() >= 8) jobs.remove(jobs.keySet().iterator().next());
        Job job = new Job(UUID.randomUUID().toString(), source.name());
        jobs.put(job.id, job);
        active = true;
        worker.submit(() -> {
            String terminalState = "FAILED";
            try {
                long start = System.nanoTime();
                job.phase = "READING";
                job.log("Đang đọc và kiểm tra dữ liệu đầu vào…");
                InputData input = source.read(su, sl, job::log);
                long imported = System.nanoTime();
                job.importMs = (imported-start)/1e6;
                job.log("Đọc hoàn tất: " + input.batches().stream().mapToInt(b -> b.transactions().size()).sum()
                        + " giao dịch. Bắt đầu khai phá PIHAUPA.");
                job.miningStarted = System.nanoTime();
                job.phase = "MINING";
                var cpu = ManagementFactory.getThreadMXBean();
                long beforeCpu = cpu.isCurrentThreadCpuTimeSupported() ? cpu.getCurrentThreadCpuTime() : -1;
                AnalysisReport report = AnalysisReport.run(input, RescanPolicy.TIGHT, job::log);
                long mined = System.nanoTime();
                job.algorithmMs = report.elapsedNanos()/1e6;
                job.sampleHeap();
                job.log("Khai phá hoàn tất. Đang tổng hợp báo cáo.");
                job.phase = "REPORTING";
                long afterCpu = beforeCpu >= 0 ? cpu.getCurrentThreadCpuTime() : -1;
                job.external = input.externalUtilities();
                job.report = report;
                String comparison = "null";
                if (compare) {
                    job.phase = "COMPARING";
                    job.log("Đối chiếu Original Eq. (5) và Tight Eq. (6); đo từng lượt riêng.");
                    ComparisonReport pair = ComparisonReport.run(input, job::log);
                    job.comparison=pair;
                    comparison = "{\"sameHaups\":" + pair.sameHaups() + ",\"original\":"
                            + pair.original().toJson() + ",\"tight\":" + pair.tight().toJson() + "}";
                }
                List<Map<String,Object>> preview = new ArrayList<>();
                for (Batch batch : input.batches()) for (Transaction t : batch.transactions()) {
                    if (preview.size() >= 30) break;
                    preview.add(Map.of("batch",batch.name(),"tid",t.tid(),"items",t.internalUtilities(),
                            "tu",t.transactionUtility(input.externalUtilities()),"mu",t.maximumUtility(input.externalUtilities())));
                }
                Map<String,Object> measurements = new LinkedHashMap<>();
                measurements.put("importMs",(imported-start)/1e6);
                measurements.put("algorithmMs",(mined-imported)/1e6);
                measurements.put("threadCpuMs",beforeCpu >= 0 && afterCpu >= beforeCpu ? (afterCpu-beforeCpu)/1e6 : null);
                measurements.put("heapPeakObservedMb",report.peakMemoryBytes()/1048576.);
                job.measurements=measurements;
                job.measurementCsv="metric,value,unit\nimportMs,"+job.importMs+",ms\nalgorithmMs,"+report.elapsedNanos()/1e6+",ms\nthreadCpuMs,"+measurements.get("threadCpuMs")+",ms\nheapPeakObservedMb,"+report.peakMemoryBytes()/1048576.+",MiB\n";
                job.result = "{\"id\":"+Json.encode(job.id)+",\"source\":"+Json.encode(job.source)
                        +",\"createdAt\":"+Json.encode(job.createdAt)+",\"report\":"+report.toJson()
                        +",\"measurement\":"+Json.encode(measurements)+",\"preview\":"+Json.encode(preview)
                        +",\"externalUtilities\":"+Json.encode(input.externalUtilities().entrySet().stream().limit(30)
                            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue)))
                        +",\"comparison\":"+comparison+"}";
                job.log("Hoàn tất. Sẵn sàng xem báo cáo và xuất TXT/CSV.");
                job.finishedNanos = System.nanoTime();
                job.phase = "COMPLETED";
                terminalState = "COMPLETED";
            } catch (OutOfMemoryError e) {
                job.fail(e);
                job.phase = "FAILED";
                job.finishedNanos = System.nanoTime();
            } catch (Exception e) {
                job.fail(e);
                job.log(job.error);
                job.finishedNanos = System.nanoTime();
                job.phase = "FAILED";
            } finally {
                try { source.cleanup(); } catch (Exception e) { job.log("Không dọn được tệp tạm: " + e.getMessage()); }
                synchronized (AnalysisService.this) { active = false; job.state = terminalState; }
            }
        });
        return job;
    }
    public synchronized Job get(String id) {
        Job job = jobs.get(id);
        if (job == null) throw new NoSuchElementException("Phiên đã hết hạn hoặc không tồn tại. Chạy lại để xuất báo cáo.");
        return job;
    }
    public Map<String,Object> runTestCases() {
        synchronized(this){if(active)throw new IllegalStateException("Đang có lượt phân tích hoặc kiểm thử chạy.");active=true;}
        try{return TestCaseRunner.run();}finally{synchronized(this){active=false;}}
    }
    public void close() { worker.shutdownNow(); }
    public static final class Job {
        public final String id, source, createdAt = Instant.now().toString();
        public volatile String state = "RUNNING", error = "", result;
        private volatile String errorCode = "", errorAction = "";
        public volatile String phase = "READING";
        private final long startedNanos = System.nanoTime();
        private volatile long finishedNanos, miningStarted;
        private volatile double importMs = -1, algorithmMs = -1;
        private double heapPeakMb;
        public volatile AnalysisReport report;
        public volatile ComparisonReport comparison;
        public volatile String measurementCsv;
        public volatile Map<String,Object> measurements;
        public volatile Map<String,Double> external;
        private final ArrayDeque<String> lines = new ArrayDeque<>();
        Job(String id, String source) { this.id=id; this.source=source; }
        private void fail(Throwable cause){AppError info=AppError.from(cause);error=info.message();errorCode=info.code();errorAction=info.action();}
        synchronized void log(String line) { if (lines.size() >= 100) lines.removeFirst(); lines.addLast(line); }
        private synchronized double sampleHeap() {
            Runtime runtime = Runtime.getRuntime();
            double used = (runtime.totalMemory()-runtime.freeMemory())/1048576.;
            heapPeakMb = Math.max(heapPeakMb,used);
            return used;
        }
        public synchronized String status() {
            double heap = sampleHeap();
            Map<String,Object> status = new LinkedHashMap<>();
            status.put("id",id); status.put("source",source); status.put("state",state);
            status.put("phase",phase); status.put("error",error); status.put("lines",List.copyOf(lines));
            status.put("code",errorCode); status.put("action",errorAction);
            status.put("elapsedMs",((finishedNanos==0?System.nanoTime():finishedNanos)-startedNanos)/1e6);
            status.put("importMs",importMs<0?null:importMs);
            Double miningMs = null;
            if (algorithmMs>=0) miningMs=algorithmMs;
            else if (phase.equals("MINING")) miningMs=(System.nanoTime()-miningStarted)/1e6;
            status.put("algorithmMs",miningMs);
            status.put("heapUsedMb",heap); status.put("heapPeakObservedMb",heapPeakMb);
            return Json.encode(status);
        }
    }
}
