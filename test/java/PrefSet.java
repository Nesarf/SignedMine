// Write a best time by hand (used once to restore a record the old code never persisted).
//   javac -encoding UTF-8 -cp build-java -d build-java test/java/PrefSet.java
//   java -cp build-java PrefSet 16 30 100 675
import java.util.prefs.Preferences;

public class PrefSet {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("用法: PrefSet <rows> <cols> <mines> <seconds>");
            return;
        }
        int r = Integer.parseInt(args[0]), c = Integer.parseInt(args[1]), m = Integer.parseInt(args[2]);
        double sec = Double.parseDouble(args[3]);
        String key = "best_" + r + "x" + c + "_" + m;
        Preferences p = Sweeper.prefs();
        p.putDouble(key, sec);
        p.flush();
        System.out.println("写入 " + p.absolutePath() + " / " + key + " = " + p.getDouble(key, -1));
    }
}
