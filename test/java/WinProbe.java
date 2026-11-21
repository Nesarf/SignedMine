// Force a finished board and call updateCounters(), then read back what got stored.
//   javac -encoding UTF-8 -cp build-java -d build-java test/java/WinProbe.java
//   java -cp build-java WinProbe
import java.util.prefs.Preferences;

public class WinProbe {
    public static void main(String[] args) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            Sweeper f = new Sweeper();
            Sweeper.Board b = Sweeper.board;
            int total = b.R * b.C;
            for (int i = 0; i < total; i++) {
                if (!b.mine[i] && !b.rev[i]) { b.rev[i] = true; b.revN++; }
            }
            System.out.println("revCount=" + b.revCount() + "  通关需要 " + (total - b.M));
            Preferences p = Sweeper.prefs();
            System.out.println("调用前 best_16x30_100 = " + p.getDouble("best_16x30_100", -1));
            f.updateCounters();
            try { p.flush(); } catch (Exception e) { System.out.println("flush 失败: " + e); }
            System.out.println("调用后 best_16x30_100 = " + p.getDouble("best_16x30_100", -1));
            System.out.println("状态栏: " + f.status.getText());
        });
        System.exit(0);
    }
}
