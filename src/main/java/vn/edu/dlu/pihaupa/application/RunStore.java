package vn.edu.dlu.pihaupa.application;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import vn.edu.dlu.pihaupa.support.Json;

/** Lưu kết quả hoàn tất trên máy; ID UUID không nhận đường dẫn từ client. */
public final class RunStore {
    private final Path root;
    public RunStore(){String value=System.getenv("PIHAUPA_RUN_ROOT");root=Path.of(value==null||value.isBlank()?"results/runs":value).toAbsolutePath().normalize();}
    private Path folder(String id){if(!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Mã lượt chạy không hợp lệ.");return root.resolve(id);}
    public void save(AnalysisService.Job job)throws IOException{
        Files.createDirectories(root);
        Path temp=Files.createTempDirectory(root,".writing-");
        try{
            Files.writeString(temp.resolve("result.json"),job.result,StandardCharsets.UTF_8);
            Files.writeString(temp.resolve("manifest.json"),Json.encode(job.manifest),StandardCharsets.UTF_8);
            Files.write(temp.resolve("analysis.zip"),ReportArchive.zip(ReportArchive.analysis(job)));
            Files.createDirectories(root);Files.move(temp,folder(job.id));
        }finally{if(Files.exists(temp))try(var paths=Files.walk(temp)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
    public String list()throws IOException{
        if(!Files.isDirectory(root))return "[]";
        try(var paths=Files.list(root)){var folders=paths.filter(Files::isDirectory).filter(p->p.getFileName().toString().matches("[a-f0-9-]{36}")).sorted(Comparator.comparingLong((Path p)->p.toFile().lastModified()).reversed()).limit(20).toList();List<String> items=new ArrayList<>();for(Path p:folders)if(Files.exists(p.resolve("manifest.json")))items.add(Files.readString(p.resolve("manifest.json")));return "["+String.join(",",items)+"]";}
    }
    public String result(String id)throws IOException{return Files.readString(folder(id).resolve("result.json"));}
    public byte[] archive(String id)throws IOException{return Files.readAllBytes(folder(id).resolve("analysis.zip"));}
}
