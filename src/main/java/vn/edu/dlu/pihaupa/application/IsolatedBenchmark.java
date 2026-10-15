package vn.edu.dlu.pihaupa.application;

import piHAUPA.*;
import vn.edu.dlu.pihaupa.support.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Đối chiếu mỗi chính sách trong JVM con riêng, tuần tự; heap vẫn là sampled JVM heap. */
public final class IsolatedBenchmark {
    public record Result(String json,String csv){}
    private IsolatedBenchmark(){}
    public static Result run(InputData input,int repeats,Consumer<String> log)throws Exception{
        Path dir=Files.createTempDirectory("pihaupa-comparison-");
        try{
            Path file=dir.resolve("input.txt");InputWriter.writeCombined(input,file);
            for(String policy:List.of("ORIGINAL","TIGHT")){
                if(Thread.currentThread().isInterrupted())throw new CancellationException();
                log.accept("Đo "+policy+" trong JVM riêng · "+repeats+" lượt.");
                String exe=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
                long heap=Runtime.getRuntime().maxMemory()/1048576;
                Process child=new ProcessBuilder(exe,"-Xmx"+heap+"m","-cp",System.getProperty("java.class.path"),IsolatedBenchmark.class.getName(),file.toString(),policy,dir.toString(),String.valueOf(repeats))
                        .redirectErrorStream(true).redirectOutput(dir.resolve(policy+".log").toFile()).start();
                try{log.accept("Đã khởi động JVM "+policy);while(!child.waitFor(200,TimeUnit.MILLISECONDS)){if(Thread.currentThread().isInterrupted())throw new CancellationException();}if(child.exitValue()!=0)throw new IOException("JVM "+policy+" thất bại: "+Files.readString(dir.resolve(policy+".log")));}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new CancellationException();}
                finally{if(child.isAlive()){boolean interrupted=Thread.interrupted();child.destroyForcibly();try{child.waitFor(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){interrupted=true;}finally{if(interrupted)Thread.currentThread().interrupt();}}}
            }
            boolean same=Files.readString(dir.resolve("ORIGINAL.haups")).equals(Files.readString(dir.resolve("TIGHT.haups")));
            String json="{\"sameHaups\":"+same+",\"isolated\":true,\"repetitions\":"+repeats+",\"original\":"+Files.readString(dir.resolve("ORIGINAL.json"))+",\"tight\":"+Files.readString(dir.resolve("TIGHT.json"))+",\"timing\":{\"original\":"+Files.readString(dir.resolve("ORIGINAL.timing"))+",\"tight\":"+Files.readString(dir.resolve("TIGHT.timing"))+"}}";
            String csv="policy,run,algorithmMs,heapPeakObservedMiB\n"+Files.readString(dir.resolve("ORIGINAL.csv"))+Files.readString(dir.resolve("TIGHT.csv"));return new Result(json,csv);
        }finally{try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
    public static void main(String[] args)throws Exception{
        InputData input=InputParser.parse(Path.of(args[0]));RescanPolicy policy=RescanPolicy.valueOf(args[1]);Path dir=Path.of(args[2]);int repeats=Integer.parseInt(args[3]);
        if(repeats>1)AnalysisReport.run(input,policy);
        List<AnalysisReport> reports=new ArrayList<>();StringBuilder csv=new StringBuilder();
        for(int i=0;i<repeats;i++){AnalysisReport r=AnalysisReport.run(input,policy);reports.add(r);csv.append(policy).append(',').append(i+1).append(',').append(r.elapsedNanos()/1e6).append(',').append(r.peakMemoryBytes()/1048576.).append('\n');}
        reports.sort(Comparator.comparingLong(AnalysisReport::elapsedNanos));AnalysisReport median=reports.get(reports.size()/2);
        Map<String,Double> haups=new TreeMap<>();median.batches().get(median.batches().size()-1).largePatterns().forEach(p->haups.put(String.join(",",p.items().stream().sorted().toList()),p.averageUtility()));
        Files.writeString(dir.resolve(policy+".haups"),Json.encode(haups));Files.writeString(dir.resolve(policy+".json"),median.toJson());Files.writeString(dir.resolve(policy+".csv"),csv.toString());
        Files.writeString(dir.resolve(policy+".timing"),Json.encode(Map.of("medianMs",median.elapsedNanos()/1e6,"minMs",reports.get(0).elapsedNanos()/1e6,"maxMs",reports.get(repeats-1).elapsedNanos()/1e6,"warmupRuns",repeats>1?1:0)));
    }
}
