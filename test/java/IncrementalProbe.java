// Verify that hints produced by the incremental path stay sound as the game goes on.
// "Only add, never rewrite" means a hint may be computed on an older board, so re-check it
// against the real mine layout at several points: lb <= real, and exact implies real == lb.
//   javac -encoding UTF-8 -cp build-java -d build-java test/java/IncrementalProbe.java
//   java -cp build-java IncrementalProbe
import java.util.*;

public class IncrementalProbe {
    public static void main(String[] args) {
        int checked = 0, badLb = 0, badExact = 0, badOver = 0, rounds = 0;
        for (int t = 0; t < 4; t++) {
            if (!Sweeper.newGame(16, 30, 100, 500 + t * 17)) continue;
            Sweeper.Board b = Sweeper.board;
            int step = 0;
            while (b.revCount() < 380 && step++ < 400) {
                int pick = -1;
                for (int i = 0; i < 480; i++) if (b.unk(i) && !b.mine[i]) { pick = i; break; }
                if (pick < 0) break;
                b.open(pick);
                Sweeper.topUpWeak();          // incremental path only
                if (step % 40 == 0) {
                    rounds++;
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
                                System.out.printf("  EXACT VIOLATION after %d opens: R%dC%d %s lb=%d real=%d hidden=%d%n",
                                        step, h.i / 30 + 1, h.i % 30 + 1, h.key, h.lb, real, hidden);
                            }
                        }
                    }
                }
            }
        }
        System.out.printf("incremental soundness: %d checks at %d snapshots, lb-broken %d, exact-broken %d, lb>hidden %d%n",
                checked, rounds, badLb, badExact, badOver);
        System.out.println(badLb + badExact + badOver == 0 ? "all sound" : "PROBLEM");
    }
}
