// Read what the game actually writes. userNodeForPackage(Sweeper.class) lands on
// "/<unnamed>" for a class in the default package, not on the user root.
//   javac -encoding UTF-8 -cp build-java -d build-java test/java/PrefProbe.java
//   java -cp build-java PrefProbe
import java.util.prefs.Preferences;

public class PrefProbe {
    public static void main(String[] args) throws Exception {
        Preferences p = Sweeper.prefs();
        System.out.println("游戏用的节点: " + p.absolutePath());
        String[] keys = p.keys();
        System.out.println("键 " + keys.length + " 个");
        for (String k : keys) System.out.println("  " + k + " = " + p.get(k, "(空)"));
        for (String k : new String[]{"best_9x9_10", "best_16x16_40", "best_16x30_100"}) {
            System.out.println("  " + k + " -> " + p.getDouble(k, -1));
        }
    }
}
