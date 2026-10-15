import piHAUPA.*;
import java.io.*;
import java.util.*;
public class OptimizationBench {
 public static void main(String[] args)throws Exception{
  Random random=new Random(71352);StringBuilder data=new StringBuilder();
  for(int row=0;row<3000;row++){
   StringBuilder items=new StringBuilder(),values=new StringBuilder();int total=0;
   for(int item=1;item<=16;item++)if(random.nextDouble()<.55){int u=1+random.nextInt(30);items.append(item).append(' ');values.append(u).append(' ');total+=u;}
   if(total==0){row--;continue;} data.append(items).append(':').append(total).append(':').append(values).append('\n');
  }
  InputData input=ArchiveDatasetReader.readTransactionsText(args.length>2?java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(args[2])):new StringReader(data.toString()),args.length>2?args[2]:"seed71352",args.length>0?Double.parseDouble(args[0]):.04,args.length>1?Double.parseDouble(args[1]):.025,x->{});
  ArrayList<Double> times=new ArrayList<>();AnalysisReport report=null;
  for(int i=0;i<7;i++){long start=System.nanoTime();report=AnalysisReport.run(input);double ms=(System.nanoTime()-start)/1e6;if(i>=2)times.add(ms);}
  Collections.sort(times);
  System.out.println("medianMs="+times.get(2)+";samples="+times+";visited="+report.stats().patternsVisited()+";joins="+report.stats().combinedNodes());
  StringBuilder signature=new StringBuilder();
  for(var batch:report.batches()){
   java.util.stream.Stream.concat(batch.largePatterns().stream(),batch.preLargePatterns().stream()).sorted(java.util.Comparator.comparing(PatternResult::displayName)).forEach(p->signature.append(batch.name()).append(p.items()).append(Double.toHexString(p.sumUtility())).append(p.type()));
  }
  System.out.println("resultSHA256="+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(signature.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
  System.out.println(report.batches().stream().map(b->b.largePatterns().size()+"/"+b.preLargePatterns().size()).toList());
 }
}
