// Dump the intermediate values of one bad "exact" hint, so the mechanism is evidence, not a guess.
//   javac -encoding UTF-8 -cp build-java -d build-java test/java/ExactDump.java
//   java -cp build-java ExactDump
import java.util.*;

public class ExactDump {
    public static void main(String[] args) {
        for (int t = 0; t < 8; t++) {
            if (!Sweeper.newGame(16, 30, 100, 1000 + t * 31)) continue;
            Sweeper.Board b = Sweeper.board;
            for (Sweeper.Hint h : Sweeper.weak) {
                List<Integer> cells = b.shapeCells(h.i, h.key);
                int real = 0, hidden = 0;
                for (int p : cells) { if (b.mine[p]) real++; if (b.unk(p)) hidden++; }
                if (h.exact && real != h.lb) { dump(b, h, cells, real, hidden); return; }
            }
        }
        System.out.println("no violation found");
    }

    static void dump(Sweeper.Board b, Sweeper.Hint h, List<Integer> cells, int real, int hidden) {
        int base = b.rest(h.i);
        Set<Integer> set = new HashSet<>(cells);
        int outside = 0;
        for (int p : b.nbs(h.i)) if (!set.contains(p) && b.unk(p)) outside++;
        System.out.printf("VIOLATION R%dC%d key=%s cellIndex=%d%n", h.i / 30 + 1, h.i % 30 + 1, h.key, h.i);
        System.out.printf("  hint: lb=%d n=%d exact=%s%n", h.lb, h.n, h.exact);
        System.out.printf("  truth: realMines=%d hiddenCells=%d%n", real, hidden);
        System.out.printf("  centre: num=%d rest=%d outside=%d%n", b.num(h.i), base, outside);
        System.out.printf("  shapeCells(all)=%s%n", cells);
        List<Integer> hiddenInShape = new ArrayList<>();
        for (int p : cells) if (b.unk(p)) hiddenInShape.add(p);
        System.out.printf("  shapeCells(unopened)=%s%n", hiddenInShape);
        for (int q2 : b.nbs(h.i)) {
            if (!b.rev[q2] || b.num(q2) == 0) continue;
            int inner = 0;
            for (int p : b.nbs(q2)) if (set.contains(p) && b.unk(p)) inner++;
            System.out.printf("  neighbour q2=R%dC%d num=%d rest=%d inner=%d rest-inner=%d%n",
                    q2 / 30 + 1, q2 % 30 + 1, b.num(q2), b.rest(q2), inner, b.rest(q2) - inner);
        }
    }
}
