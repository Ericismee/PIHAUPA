package piHAUPA;

import vn.edu.dlu.pihaupa.application.DatasetProfile;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;

/** Optional integration test: each supplied dataset runs in its own JVM. */
public final class DatasetProfileVerification {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[0]);
        String name=file.getParent().getFileName().toString();
        DatasetProfile p=DatasetProfile.find(name,Files.size(file));
        if(p==null)throw new AssertionError("Missing dataset profile: "+name);
        InputData input;
        try(var reader=Files.newBufferedReader(file)) {
            input=ArchiveDatasetReader.readTransactionsText(reader,name,p.su(),p.sl(),line->{});
        }
        AnalysisReport report=AnalysisReport.run(input);
        var last=report.batches().get(report.batchCount()-1);
        if(report.transactionCount()!=p.rows() || last.totalTransactionUtility()!=p.totalTU())throw new AssertionError("Profile input mismatch");
        PatternResult strongest=last.largePatterns().stream()
            .filter(pattern->pattern.items().equals(List.of(p.strongestItem()))).findFirst().orElseThrow();
        if(Math.abs(strongest.averageUtility()-p.singletonAU())>1e-5)throw new AssertionError("Strongest singleton AU mismatch");
        // Independently sum raw item utilities, including multi-item HAUPs, with decimal arithmetic.
        List<PatternResult> selected=last.largePatterns().stream().limit(10).toList();
        Map<String,BigDecimal> sums=new HashMap<>();
        try(var reader=Files.newBufferedReader(file)) {
            String line;
            while((line=reader.readLine())!=null) {
                if(line.isBlank())continue;
                String[] fields=line.split(":");
                String[] items=fields[0].trim().split("\\s+"),utilities=fields[2].trim().split("\\s+");
                Map<String,String> row=new HashMap<>();
                for(int i=0;i<items.length;i++)row.put(items[i],utilities[i]);
                for(var pattern:selected) if(row.keySet().containsAll(pattern.items())) {
                    BigDecimal sum=BigDecimal.ZERO;
                    for(String item:pattern.items())sum=sum.add(new BigDecimal(row.get(item)));
                    sums.merge(pattern.displayName(),sum,BigDecimal::add);
                }
            }
        }
        for(var pattern:selected) if(sums.get(pattern.displayName()).compareTo(BigDecimal.valueOf(pattern.sumUtility()))!=0)
            throw new AssertionError("Raw U(P) mismatch: "+pattern.displayName());
        Files.createDirectories(Path.of("tmp/profile-verification"));
        Files.writeString(Path.of("tmp/profile-verification",name+".json"),report.toJson());
        System.out.printf(Locale.US,"PASS %s Su=%.10f Sl=%.10f rows=%d HAUP=%d PRE-LARGE=%d rawPatternsChecked=%d timeMs=%.1f heapMiB=%.1f%n",
            name,p.su(),p.sl(),report.transactionCount(),last.largePatterns().size(),last.preLargePatterns().size(),selected.size(),report.elapsedNanos()/1e6,report.peakMemoryBytes()/1048576.);
    }
}
