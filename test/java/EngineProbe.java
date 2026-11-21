// Headless probe for the Java engine: deal cost/success, hint soundness, mid-game recompute cost.
// Not part of the app. Compile against build-java and run:
//   javac -encoding UTF-8 -cp build-java -d build-java test/java/EngineProbe.java
//   java -cp build-java EngineProbe [rounds]
//
// Output is English on purpose: console code pages mangle CJK on Windows.
import java.util.*;

public class EngineProbe {
    static final Random RND = new Random(20261015);

    public static void main(String[] args) {
        int rounds = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        for (int[] lv : new int[][]{{9, 9, 10}, {16, 16, 40}, {16, 30, 100}}) {
            int ok = 0;
            long t0 = System.nanoTime();
            for (int t = 0; t < rounds; t++) {
                if (Sweeper.newGame(lv[0], lv[1], lv[2], RND.nextInt(1 << 20))) ok++;
            }
            double ms = (System.nanoTime() - t0) / 1e6 / rounds;
            System.out.printf("%dx%d/%d  newGame %d tries: ok %d, fail %d, avg %.1f ms%n",
                    lv[0], lv[1], lv[2], rounds, ok, rounds - ok, ms);
        }
        probeHints();
        probeMidgame();
    }

    // Every hint must satisfy: lb <= realMines <= hidden. "exact" claims realMines == lb.
    static void probeHints() {
        int checked = 0, badLb = 0, badExact = 0, badOver = 0, failed = 0;
        for (int t = 0; t < 6; t++) {
            if (!Sweeper.newGame(16, 30, 100, 1000 + t * 31)) { failed++; continue; }
            Sweeper.Board b = Sweeper.board;
            for (Sweeper.Hint h : Sweeper.weak) {
                List<Integer> cells = b.shapeCells(h.i, h.key);
                int real = 0, hidden = 0;
                for (int p : cells) { if (b.mine[p]) real++; if (b.unk(p)) hidden++; }
                checked++;
                if (real < h.lb) badLb++;
                if (h.lb > hidden) badOver++;
                if (h.exact && real != h.lb) {
                    badExact++;
                    if (badExact <= 3) {
                        System.out.printf("  EXACT VIOLATION R%dC%d %s lb=%d real=%d hidden=%d%n",
                                h.i / 30 + 1, h.i % 30 + 1, h.key, h.lb, real, hidden);
                    }
                }
            }
        }
        System.out.printf("hint soundness: %d hints, lb-broken %d, exact-broken %d, lb>hidden %d, deals failed %d%n",
                checked, badLb, badExact, badOver, failed);
    }

    static void probeMidgame() {
        if (!Sweeper.newGame(16, 30, 100, 777)) { System.out.println("midgame: deal failed"); return; }
        Sweeper.Board b = Sweeper.board;
        int guard = 0;
        while (b.revCount() < 240 && guard++ < 1000) {
            for (int i = 0; i < 480; i++) if (b.unk(i) && !b.mine[i]) { b.open(i); break; }
        }
        long t0 = System.nanoTime();
        int n = 20;
        for (int k = 0; k < n; k++) b.weakHints();
        double ms = (System.nanoTime() - t0) / 1e6 / n;
        System.out.printf("midgame weakHints (opened %d cells): %.2f ms per call%n", b.revCount(), ms);
    }
}
