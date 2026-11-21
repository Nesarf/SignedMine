// Does writing to Preferences work at all here, and does the default package resolve sanely?
//   javac -encoding UTF-8 -cp build-java -d build-java test/java/PrefWriteProbe.java
//   java -cp build-java PrefWriteProbe
import java.util.prefs.Preferences;

public class PrefWriteProbe {
    public static void main(String[] args) throws Exception {
        Preferences byPackage = Preferences.userNodeForPackage(Sweeper.class);
        System.out.println("userNodeForPackage(Sweeper.class) -> " + byPackage.absolutePath());

        Preferences explicit = Preferences.userRoot().node("SignedMine");
        System.out.println("userRoot().node(\"SignedMine\")    -> " + explicit.absolutePath());

        try {
            byPackage.putDouble("write_test_pkg", 42.5);
            byPackage.flush();
            System.out.println("包节点写入后读回: " + byPackage.getDouble("write_test_pkg", -1));
        } catch (Throwable t) {
            System.out.println("包节点写入失败: " + t);
        }

        try {
            explicit.putDouble("write_test_node", 7.5);
            explicit.flush();
            System.out.println("显式节点写入后读回: " + explicit.getDouble("write_test_node", -1));
        } catch (Throwable t) {
            System.out.println("显式节点写入失败: " + t);
        }

        System.out.println("userRoot keys: " + String.join(",", Preferences.userRoot().keys()));
        System.out.println("SignedMine keys: " + String.join(",", explicit.keys()));
    }
}
