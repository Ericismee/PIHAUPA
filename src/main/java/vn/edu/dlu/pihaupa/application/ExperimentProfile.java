package vn.edu.dlu.pihaupa.application;

import piHAUPA.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Dấu vết thực nghiệm: hash dữ liệu + môi trường JVM; không hash ngưỡng vào dataset identity. */
public final class ExperimentProfile {
    private ExperimentProfile() {}
    public static Map<String,Object> capture(InputSource source,InputData input,int repeats)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        Path file=source.file();String hashKind;
        String capturedHash=source.rawSha256();
        if(file!=null && capturedHash!=null){
            hashKind="raw-file-sha256";
        }else if(file!=null){
            hashKind="raw-file-sha256";
            try(var in=Files.newInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){check();digest.update(buffer,0,n);}}
        }else{
            hashKind="canonical-input-sha256-v1";
            try(var out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))){
                var eu=new TreeMap<>(input.externalUtilities());out.writeInt(eu.size());for(var e:eu.entrySet()){out.writeUTF(e.getKey());out.writeDouble(e.getValue());}
                out.writeInt(input.batches().size());for(var b:input.batches()){check();out.writeUTF(b.name());out.writeInt(b.transactions().size());for(var t:b.transactions()){out.writeUTF(t.tid());var iu=new TreeMap<>(t.internalUtilities());out.writeInt(iu.size());for(var e:iu.entrySet()){out.writeUTF(e.getKey());out.writeDouble(e.getValue());}}}
            }
        }
        Map<String,Object> m=new LinkedHashMap<>();m.put("schemaVersion",1);m.put("insertionSteps",source.insertionSteps());m.put("originalRatio",source.insertionSteps()>0?source.originalRatio():null);m.put("cpuDescription",System.getenv().getOrDefault("PROCESSOR_IDENTIFIER",System.getProperty("os.arch")));m.put("sizePercent",source.sizePercent());m.put("selection",source.sizePercent()==100?"full-input":"ordered-prefix-repartition-80-10-10");m.put("sourceBytes",file==null?null:Files.size(file));m.put("source",source.name());m.put("format",source.format());m.put("sourceKind",source.kind());m.put("sha256",capturedHash!=null?capturedHash:HexFormat.of().formatHex(digest.digest()));m.put("hashKind",hashKind);m.put("hashReadMode",capturedHash!=null?"single-pass-read-and-hash":file!=null?"separate-pass":"canonical-input");
        Properties build=new Properties();try(var resource=ExperimentProfile.class.getResourceAsStream("/build-info.properties")){if(resource!=null)build.load(resource);}
        m.put("revision",build.getProperty("revision","unavailable"));m.put("workingTreeDirty",build.getProperty("dirty","unknown"));m.put("applicationVersion",build.getProperty("version","2.0.0"));
        m.put("javaVersion",System.getProperty("java.version"));m.put("javaVendor",System.getProperty("java.vendor"));m.put("os",System.getProperty("os.name"));m.put("architecture",System.getProperty("os.arch"));m.put("processors",Runtime.getRuntime().availableProcessors());m.put("maxHeapMiB",Runtime.getRuntime().maxMemory()/1048576.);
        m.put("su",input.upperThreshold());m.put("sl",input.lowerThreshold());m.put("measuredRuns",repeats);m.put("warmupRuns",repeats>1?1:0);m.put("batches",input.batches().stream().map(b->Map.of("name",b.name(),"rows",b.transactions().size())).toList());m.put("heapDefinition","JVM heap quan sát; không phải RSS; các lượt trong JVM chính dùng chung heap.");return m;
    }
    private static void check(){if(Thread.currentThread().isInterrupted())throw new CancellationException();}
}
