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
    private final RunStore store=new RunStore();
    /**
     * DESIGN PATTERN — FACADE (điểm vào điều phối phân tích).
     * Controller gọi submit; service phối hợp đọc, mining, đo, đối chiếu và cleanup.
     * Nhận InputSource theo Strategy: không viết lại từng cách mở dữ liệu tại đây.
     * Single worker là chính sách thực thi, không phải Singleton hay lợi ích mặc định của Facade.
     */
    public synchronized Job submit(InputSource source, double su, double sl, boolean compare) {
        return submit(source,su,sl,compare,1);
    }
    public synchronized Job submit(InputSource source,double su,double sl,boolean compare,int repeats) {
        if(!Set.of(1,3,5).contains(repeats))throw new IllegalArgumentException("Số lượt đo phải là 1, 3 hoặc 5.");
        if (active) throw new IllegalStateException("Một phép đo đang chạy. Hãy đợi hoàn tất.");
        if (!(sl > 0 && sl < su && su <= 1)) throw new IllegalArgumentException("Cần 0 < Sl < Su ≤ 1.");
        while (jobs.size() >= 8) jobs.remove(jobs.keySet().iterator().next());
        Job job = new Job(UUID.randomUUID().toString(), source.name());
        jobs.put(job.id, job);
        active = true;
        worker.submit(() -> {
            job.executionThread=Thread.currentThread();
            String terminalState = "FAILED";
            ResourceMonitor resources = new ResourceMonitor();
            try {
                long start = System.nanoTime();
                job.phase = "READING";
                job.log("Đang đọc và kiểm tra dữ liệu đầu vào…");
                InputData input = source.read(su, sl, job::log);
                job.checkCancelled();
                job.manifest=new LinkedHashMap<>(ExperimentProfile.capture(source,input,repeats));
                job.manifest.put("id",job.id);job.manifest.put("createdAt",job.createdAt);
                job.checkCancelled();
                long imported = System.nanoTime();
                job.importMs = (imported-start)/1e6;
                job.log("Đọc hoàn tất: " + input.batches().stream().mapToInt(b -> b.transactions().size()).sum()
                        + " giao dịch. Bắt đầu khai phá PIHAUPA.");
                job.miningStarted = System.nanoTime();
                job.phase = "MINING";
                var cpu = ManagementFactory.getThreadMXBean();
                long beforeCpu = cpu.isCurrentThreadCpuTimeSupported() ? cpu.getCurrentThreadCpuTime() : -1;
                if(repeats>1){job.log("Warm-up: 1 lượt Tight, không đưa vào thống kê.");AnalysisReport.run(input,RescanPolicy.TIGHT,job::log);}
                List<AnalysisReport> measured=new ArrayList<>();
                for(int i=0;i<repeats;i++){job.checkCancelled();job.log("Đo Tight "+(i+1)+"/"+repeats);measured.add(AnalysisReport.run(input,RescanPolicy.TIGHT,job::log));}
                var sorted=measured.stream().sorted(Comparator.comparingLong(AnalysisReport::elapsedNanos)).toList();
                AnalysisReport report=sorted.get(sorted.size()/2);
                job.timingSamples=measured.stream().map(r->r.elapsedNanos()/1e6).toList();
                long mined = System.nanoTime();
                job.algorithmMs = report.elapsedNanos()/1e6;
                job.sampleHeap();
                job.log("Khai phá hoàn tất. Đang tổng hợp báo cáo.");
                job.phase = "REPORTING";
                long afterCpu = beforeCpu >= 0 ? cpu.getCurrentThreadCpuTime() : -1;
                job.checkCancelled();
                job.log("Kiểm chứng: đối chiếu oracle độc lập nếu dữ liệu trong phạm vi hỗ trợ.");
                job.oracle=SmallDataOracle.verify(input,report);
                job.log("Oracle: "+job.oracle.get("state"));
                job.external = input.externalUtilities();
                job.report = report;
                String comparison = "null";
                if (compare) {
                    job.phase = "COMPARING";
                    job.log("Đối chiếu Original/Tight: mỗi chính sách trong JVM con riêng.");
                    IsolatedBenchmark.Result pair=IsolatedBenchmark.run(input,repeats,job::log);
                    comparison=pair.json();job.isolatedComparisonCsv=pair.csv();

                }
                job.phase="REPORTING";job.log("Đang tổng hợp và lưu hồ sơ thực nghiệm.");
                List<Map<String,Object>> preview = new ArrayList<>();
                for (Batch batch : input.batches()) for (Transaction t : batch.transactions()) {
                    if (preview.size() >= 30) break;
                    preview.add(Map.of("batch",batch.name(),"tid",t.tid(),"items",t.internalUtilities(),
                            "tu",t.transactionUtility(input.externalUtilities()),"mu",t.maximumUtility(input.externalUtilities())));
                }
                Map<String,Object> measurements = new LinkedHashMap<>();
                measurements.put("importMs",(imported-start)/1e6);
                measurements.put("algorithmMs",report.elapsedNanos()/1e6);
                measurements.put("algorithmTotalMs",(mined-imported)/1e6);
                measurements.put("samplesMs",job.timingSamples);measurements.put("medianMs",report.elapsedNanos()/1e6);
                measurements.put("minMs",sorted.get(0).elapsedNanos()/1e6);measurements.put("maxMs",sorted.get(sorted.size()-1).elapsedNanos()/1e6);
                measurements.put("repetitions",repeats);
                measurements.put("runMeasurements",measured.stream().map(r->Map.of("runtimeMs",r.elapsedNanos()/1e6,"heapPeakMiB",r.peakMemoryBytes()/1048576.)).toList());
                measurements.put("threadCpuMs",beforeCpu >= 0 && afterCpu >= beforeCpu ? (afterCpu-beforeCpu)/1e6 : null);
                measurements.put("heapPeakObservedMb",report.peakMemoryBytes()/1048576.);
                measurements.put("resources",resources.snapshot());
                measurements.put("processingMs",(System.nanoTime()-start)/1e6);
                job.measurements=measurements;
                job.measurementCsv="metric,value,unit\nimportMs,"+job.importMs+",ms\nalgorithmMs,"+report.elapsedNanos()/1e6+",ms\nthreadCpuMs,"+measurements.get("threadCpuMs")+",ms\nheapPeakObservedMb,"+report.peakMemoryBytes()/1048576.+",MiB\n";
                job.manifest.put("haups",report.batches().get(report.batches().size()-1).largePatterns().size());job.manifest.put("rows",report.transactionCount());job.manifest.put("medianMs",report.elapsedNanos()/1e6);job.manifest.put("comparison",compare);job.manifest.put("comparisonIsolation",compare?"one-child-jvm-per-policy":"not-run");job.manifest.put("oracle",job.oracle);
                job.result = "{\"id\":"+Json.encode(job.id)+",\"source\":"+Json.encode(job.source)
                        +",\"createdAt\":"+Json.encode(job.createdAt)+",\"report\":"+report.toJson()
                        +",\"processLog\":"+Json.encode(job.logLines())+",\"manifest\":"+Json.encode(job.manifest)+",\"oracle\":"+Json.encode(job.oracle)+",\"measurement\":"+Json.encode(measurements)+",\"preview\":"+Json.encode(preview)
                        +",\"externalUtilities\":"+Json.encode(input.externalUtilities().entrySet().stream().limit(30)
                            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue)))
                        +",\"comparison\":"+comparison+"}";
                synchronized(AnalysisService.this){job.checkCancelled();job.publicationStarted=true;}
                try{store.save(job);job.storageState="SAVED";job.log("Đã lưu hồ sơ và ZIP trên máy.");}catch(java.io.IOException e){job.storageState="FAILED";job.storageError=e.getMessage();job.log("Lưu hồ sơ thất bại: "+e.getMessage()+". Hãy tải ZIP trước khi đóng phiên.");}
                job.log("Hoàn tất. Sẵn sàng xem báo cáo và xuất TXT/CSV.");
                job.finishedNanos = System.nanoTime();
                job.phase = "COMPLETED";
                terminalState = "COMPLETED";
            } catch(CancellationException e){
                terminalState="CANCELLED";job.phase="CANCELLED";job.finishedNanos=System.nanoTime();synchronized(job){job.lines.addLast("Đã hủy lượt chạy; không sử dụng kết quả dở dang.");}
            } catch (OutOfMemoryError e) {
                job.fail(e);
                job.phase = "FAILED";
                job.finishedNanos = System.nanoTime();
            } catch (Exception e) {
                if(job.cancelRequested||Thread.currentThread().isInterrupted()){terminalState="CANCELLED";job.phase="CANCELLED";job.result=null;}
                else{job.fail(e);synchronized(job){job.lines.addLast(job.error);}}
                job.finishedNanos = System.nanoTime();
                if(!terminalState.equals("CANCELLED"))job.phase = "FAILED";
            } finally {
                resources.close();
                try { source.cleanup(); } catch (Exception e) { synchronized(job){job.lines.addLast("Không dọn được tệp tạm: "+e.getMessage());} }
                synchronized (AnalysisService.this) {if(job.cancelRequested && !terminalState.equals("FAILED")){terminalState="CANCELLED";job.result=null;job.phase="CANCELLED";}job.executionThread=null;active = false; job.state = terminalState;}
                Thread.interrupted();
            }
        });
        return job;
    }
    public synchronized Job get(String id) {
        Job job = jobs.get(id);
        if (job == null) throw new NoSuchElementException("Phiên đã hết hạn hoặc không tồn tại. Chạy lại để xuất báo cáo.");
        return job;
    }
    public Map<String,Object> verifyPaper(){
        synchronized(this){if(active)throw new IllegalStateException("Đang có lượt phân tích hoặc kiểm thử chạy.");active=true;}
        try{return PaperVerification.run();}finally{synchronized(this){active=false;}}
    }
    public Map<String,Object> runTestCases() {
        synchronized(this){if(active)throw new IllegalStateException("Đang có lượt phân tích hoặc kiểm thử chạy.");active=true;}
        try{return TestCaseRunner.run();}finally{synchronized(this){active=false;}}
    }
    public synchronized Job cancel(String id){Job job=get(id);if(job.state.equals("RUNNING")&&!job.publicationStarted){job.cancelRequested=true;job.phase="CANCELLING";if(job.executionThread!=null)job.executionThread.interrupt();}return job;}
    public RunStore store(){return store;}
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
        private long rowsRead;
        public volatile AnalysisReport report;
        public volatile ComparisonReport comparison;
        public volatile String measurementCsv,isolatedComparisonCsv;
        public volatile String storageState="PENDING",storageError="";
        private volatile boolean publicationStarted;
        public volatile Map<String,Object> manifest,oracle;
        public volatile List<Double> timingSamples=List.of();
        private volatile Thread executionThread;
        private volatile boolean cancelRequested;
        private void checkCancelled(){if(cancelRequested||Thread.currentThread().isInterrupted())throw new CancellationException();}
        public volatile Map<String,Object> measurements;
        public volatile Map<String,Double> external;
        private final ArrayDeque<String> lines = new ArrayDeque<>();
        Job(String id, String source) { this.id=id; this.source=source; }
        private void fail(Throwable cause){AppError info=AppError.from(cause);error=info.message();errorCode=info.code();errorAction=info.action();}
        synchronized void log(String line) { checkCancelled(); if (lines.size() >= 500) lines.removeFirst(); lines.addLast(String.format(java.util.Locale.ROOT,"[+%.2f s] %s",(System.nanoTime()-startedNanos)/1e9,line));
            var matcher=java.util.regex.Pattern.compile("đã đọc ([0-9]+) giao dịch").matcher(line.toLowerCase(java.util.Locale.ROOT));if(matcher.find())rowsRead=Math.max(rowsRead,Long.parseLong(matcher.group(1)));
        }
        public synchronized List<String> logLines(){return List.copyOf(lines);}
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
            status.put("canCancel",state.equals("RUNNING")&&!publicationStarted);
            status.put("phase",phase); status.put("error",error); status.put("lines",List.copyOf(lines));
            status.put("code",errorCode); status.put("action",errorAction);
            status.put("elapsedMs",((finishedNanos==0?System.nanoTime():finishedNanos)-startedNanos)/1e6);
            status.put("importMs",importMs<0?null:importMs);
            Double miningMs = null;
            if (algorithmMs>=0) miningMs=algorithmMs;
            else if (phase.equals("MINING")) miningMs=(System.nanoTime()-miningStarted)/1e6;
            status.put("algorithmMs",miningMs);
            status.put("storageState",storageState);status.put("storageError",storageError);
            status.put("rowsRead",rowsRead);
            status.put("heapUsedMb",heap); status.put("heapPeakObservedMb",heapPeakMb);
            return Json.encode(status);
        }
    }
}
