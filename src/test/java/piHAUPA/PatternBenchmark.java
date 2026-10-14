package piHAUPA;
import java.nio.file.Path;
import java.util.*;
import java.lang.management.ManagementFactory;
/**
 * Công cụ đo pipeline phân tích, không đo thời gian giao diện hay xuất file.
 * Đọc input trước vùng đo; chạy 3 lượt làm nóng rồi lấy trung vị 7 lượt.
 * Byte cấp phát thuộc thread hiện tại, không phải đỉnh heap/RSS của tiến trình.
 * Thời gian và byte được sắp xếp riêng nên hai trung vị có thể thuộc hai lượt.
 * So sánh Original/Tight kiểm tra HAUP cuối, số quét lại và node kết hợp.
 * Không dùng phép đo này để khẳng định design pattern tự làm thuật toán nhanh hơn.
 */
public class PatternBenchmark {
 public static void main(String[] args) throws Exception {
  InputData input = InputParser.parse(Path.of(args[0]));
  var bean = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
  bean.setThreadAllocatedMemoryEnabled(true);
  long id = Thread.currentThread().getId();
  for (int i=0;i<3;i++) ComparisonReport.run(input);
  long[] times=new long[7], bytes=new long[7];
  ComparisonReport report=null;
  for(int i=0;i<7;i++) {long b=bean.getThreadAllocatedBytes(id), t=System.nanoTime(); report=ComparisonReport.run(input); times[i]=System.nanoTime()-t;bytes[i]=bean.getThreadAllocatedBytes(id)-b;}
  Arrays.sort(times);Arrays.sort(bytes);
  System.out.printf(Locale.ROOT,"rows=%d,median_ms=%.3f,allocated_bytes=%d,same_haups=%s,original_rescans=%d,tight_rescans=%d,original_nodes=%d,tight_nodes=%d%n",report.tight().transactionCount(),times[3]/1e6,bytes[3],report.sameHaups(),report.original().stats().rescanCount(),report.tight().stats().rescanCount(),report.original().stats().combinedNodes(),report.tight().stats().combinedNodes());
 }
}
