// Remove the keys the probes wrote, so the player's own record is not polluted.
//   javac -encoding UTF-8 -cp build-java -d build-java test/java/PrefClean.java
//   java -cp build-java PrefClean
import java.util.prefs.Preferences;

public class PrefClean {
    public static void main(String[] args) throws Exception {
        Preferences named = Sweeper.prefs();
        for (String k : new String[]{"write_test_node", "write_test_pkg", "best_16x30_100", "best_9x9_10", "best_16x16_40"}) {
            named.remove(k);
        }
        named.flush();

        Preferences pkg = Preferences.userNodeForPackage(Sweeper.class);
        for (String k : new String[]{"write_test_pkg", "best_16x30_100"}) {
            pkg.remove(k);
        }
        pkg.flush();

        System.out.println("已清理：" + named.absolutePath() + " 与 " + pkg.absolutePath());
        System.out.println("SignedMine 节点剩余: " + named.getDouble("best_16x30_100", -1));
    }
}
