import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.*;
import java.nio.file.*;

/** One-time compatible reporting hot update; keeps the JVM and mining worker alive. */
public final class ReportStorageAttach {

  public static void main(String[] args) throws Exception {
    VirtualMachine vm = VirtualMachine.attach(args[0]);
    try {
      vm.loadAgent(args[1], args[2]);
    } finally {
      vm.detach();
    }
  }

  public static final class Agent {

    public static void agentmain(String file, Instrumentation instrumentation) throws Exception {
      java.util.List<ClassDefinition> updates = new java.util.ArrayList<>();
      for (String entry : file.split("\\|")) {
        String name = entry.replace('\\', '/');
        name = name
          .substring(name.indexOf("/target/classes/") + "/target/classes/".length())
          .replace('/', '.')
          .replace(".class", "");
        boolean found = false;
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
          if (type.getName().equals(name)) {
            updates.add(new ClassDefinition(type, Files.readAllBytes(Path.of(entry))));
            found = true;
            break;
          }
        }
        if (!found) throw new IllegalStateException("Class not loaded: " + name);
      }
      instrumentation.redefineClasses(updates.toArray(ClassDefinition[]::new));
      System.setProperty("pihaupa.diskReports", "true");
    }
  }
}
