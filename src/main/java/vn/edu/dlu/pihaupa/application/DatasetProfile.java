package vn.edu.dlu.pihaupa.application;

import java.util.*;
import java.io.*;

/** Reproducible starting thresholds for the supplied utility files, not paper benchmarks. */
public record DatasetProfile(double su, double sl, String strongestItem, double singletonAU,
                             double totalTU, long rows, long bytes, double upperFraction, double lowerFraction) {
    private static final Properties PROFILES = new Properties();
    static {
        try (InputStream in = DatasetProfile.class.getResourceAsStream("/dataset-profiles.properties")) {
            if (in != null) PROFILES.load(in);
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    public static DatasetProfile find(String name, long bytes) {
        String key = name.toLowerCase(Locale.ROOT).replace("-metadata", "");
        String row = PROFILES.getProperty(key);
        if (row == null) return null;
        String[] values = row.split(",");
        long expectedBytes = Long.parseLong(values[4]);
        if (bytes != expectedBytes) return null;
        double total = Double.parseDouble(values[1]), singleton = Double.parseDouble(values[2]);
        double upper = Double.parseDouble(values[5]), lower = Double.parseDouble(values[6]);
        return new DatasetProfile(upper*singleton/total, lower*singleton/total, values[3], singleton,
                total, Long.parseLong(values[0]),expectedBytes,upper,lower);
    }
    public Map<String,Object> toMap() {
        return Map.of("su",su,"sl",sl,"strongestItem",strongestItem,"singletonAU",singletonAU,
                "totalTU",totalTU,"rows",rows,"basis","data-singleton",
                "description",String.format(Locale.US,"Ngưỡng khởi đầu từ dữ liệu: Su = %.0f%%, Sl = %.0f%% của max AU(singleton)/TU(DB). Không phải ngưỡng benchmark của bài báo.",upperFraction*100,lowerFraction*100));
    }
}
