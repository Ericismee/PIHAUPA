package piHAUPA;

import java.nio.file.*;
import java.util.*;
import java.lang.management.ManagementFactory;

/** Đo trước/sau trên cùng tệp/ngưỡng; JVM riêng, không gồm UI/resource sampler. */
public final class PipelineBenchmark {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]); double su=Double.parseDouble(args[1]),sl=Double.parseDouble(args[2]);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true); long tid=Thread.currentThread().getId();
        List<Double> read=new ArrayList<>(),mine=new ArrayList<>(); List<Long> allocated=new ArrayList<>();
        String signature=""; int rows=0,haups=0,pre=0; long rescans=0;
        for(int run=0;run<4;run++){
            long bytes=bean.getThreadAllocatedBytes(tid),start=System.nanoTime();
            InputData input;try(var reader=Files.newBufferedReader(path)){input=ArchiveDatasetReader.readTransactionsText(reader,path.getParent().getFileName().toString(),su,sl,line->{});}
            long imported=System.nanoTime(); AnalysisReport report=AnalysisReport.run(input); long end=System.nanoTime();
            StringBuilder canonical=new StringBuilder();
            for(var batch:report.batches())canonical.append(batch.action()).append(batch.largePatterns()).append(batch.preLargePatterns());
            String current=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            if(!signature.isEmpty()&&!signature.equals(current))throw new AssertionError("Non-deterministic result");signature=current;
            if(run>0){read.add((imported-start)/1e6);mine.add((end-imported)/1e6);allocated.add(bean.getThreadAllocatedBytes(tid)-bytes);}
            var last=report.batches().get(report.batchCount()-1);rows=report.transactionCount();haups=last.largePatterns().size();pre=last.preLargePatterns().size();rescans=report.stats().rescanCount();
        }
        Collections.sort(read);Collections.sort(mine);Collections.sort(allocated);
        System.out.printf(Locale.ROOT,"rows=%d su=%.12f sl=%.12f readMedianMs=%.3f miningMedianMs=%.3f allocatedMedianBytes=%d haups=%d pre=%d rescans=%d signature=%s%n",rows,su,sl,read.get(1),mine.get(1),allocated.get(1),haups,pre,rescans,signature);
    }
}
