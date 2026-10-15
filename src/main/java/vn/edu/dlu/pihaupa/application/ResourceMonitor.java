package vn.edu.dlu.pihaupa.application;

import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Lấy mẫu tài nguyên của JVM server và toàn máy; không quy RAM hệ thống cho riêng thuật toán. */
public final class ResourceMonitor implements AutoCloseable {
    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "resource-sampler"); t.setDaemon(true); return t;
    });
    private final List<Map<String,Object>> samples = new ArrayList<>();
    private final long started = System.nanoTime();
    public ResourceMonitor() { sample(); sampler.scheduleWithFixedDelay(this::sample, 1, 1, TimeUnit.SECONDS); }
    public static Map<String,Object> instant() {
        Map<String,Object> m = new LinkedHashMap<>();
        Runtime r = Runtime.getRuntime();
        m.put("heapUsedMiB", (r.totalMemory()-r.freeMemory())/1048576.);
        m.put("heapCommittedMiB", r.totalMemory()/1048576.);
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            m.put("systemTotalMiB", os.getTotalMemorySize()/1048576.);
            m.put("systemUsedMiB", (os.getTotalMemorySize()-os.getFreeMemorySize())/1048576.);
        }
        return m;
    }
    private static Double residentMiB() {
        try {
            if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
                Process p = new ProcessBuilder("tasklist", "/FI", "PID eq "+ProcessHandle.current().pid(), "/FO", "CSV", "/NH").start();
                if (!p.waitFor(2, TimeUnit.SECONDS)) { p.destroyForcibly(); return null; }
                String line = new String(p.getInputStream().readAllBytes()).trim();
                int end=line.lastIndexOf('"'), begin=line.lastIndexOf('"',end-1);
                if(begin<0)return null;
                String digits=line.substring(begin+1,end).replaceAll("[^0-9]", "");
                return digits.isEmpty()?null:Double.parseDouble(digits)/1024.;
            }
            for(String line:Files.readAllLines(Path.of("/proc/self/status")))
                if(line.startsWith("VmRSS:"))return Double.parseDouble(line.replaceAll("[^0-9]", ""))/1024.;
        } catch(Exception ignored) { /* Không hỗ trợ: trả null, không giả số 0. */ }
        return null;
    }
    private synchronized void sample() {
        Map<String,Object> m=instant(); m.put("elapsedMs",(System.nanoTime()-started)/1e6);
        m.put("processResidentMiB",residentMiB()); samples.add(m);
    }
    public synchronized Map<String,Object> snapshot() {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("intervalMs",1000); m.put("scope","JVM server và RAM toàn máy; không gồm JVM con Original/Tight; có overhead lấy mẫu.");
        m.put("samples",List.copyOf(samples));
        for(String key:List.of("heapUsedMiB","processResidentMiB","systemUsedMiB"))
            m.put(key+"Peak",samples.stream().map(s->s.get(key)).filter(Number.class::isInstance).map(Number.class::cast).mapToDouble(Number::doubleValue).max().stream().boxed().findFirst().orElse(null));
        return m;
    }
    public void close() { sampler.shutdownNow(); }
}
