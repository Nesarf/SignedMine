// Cross-implementation baseline: print one JSON line per (case, seed) with the mine layout
// and the opening position. lib/core.js's build() must produce the same line.
//   javac -encoding UTF-8 -cp build-java -d build-java test/java/CrossProbe.java
//   java -cp build-java CrossProbe > build-java/cross-java.jsonl
//
// Hints are deliberately left out here: the two implementations iterate cells in different
// orders (Set insertion order vs index order), so tie-breaking inside pickCandidate can differ.
// Mine layout and the opening position depend only on the RNG and open(), which are aligned.
import java.util.*;

public class CrossProbe {
    public static void main(String[] args) {
        int[][] cases = {{9, 9, 10}, {16, 16, 40}, {16, 30, 100}};
        int[] seeds = {1, 2, 3, 7, 12345, 999999, 20261015};
        if (args.length > 0) {
            seeds = new int[args.length];
            for (int i = 0; i < args.length; i++) seeds[i] = Integer.parseInt(args[i]);
        }
        StringBuilder sb = new StringBuilder();
        for (int[] lv : cases) {
            for (int seed : seeds) {
                ArrayList<Sweeper.Hint> hs = new ArrayList<>();
                Sweeper.Board b = Sweeper.buildOnce(lv[0], lv[1], lv[2], seed, hs);
                int n = lv[0] * lv[1];
                sb.setLength(0);
                sb.append("{\"case\":\"").append(lv[0]).append('x').append(lv[1]).append('/').append(lv[2]).append('"');
                sb.append(",\"seed\":").append(seed);
                sb.append(",\"ok\":").append(b != null);
                sb.append(",\"mines\":[");
                if (b != null) {
                    boolean first = true;
                    for (int i = 0; i < n; i++) if (b.mine[i]) { if (!first) sb.append(','); first = false; sb.append(i); }
                }
                sb.append("],\"snapshot\":[");
                if (b != null && b.snapshot != null) {
                    boolean first = true;
                    for (int i = 0; i < n; i++) if (b.snapshot[i]) { if (!first) sb.append(','); first = false; sb.append(i); }
                }
                sb.append("],\"hints\":[");
                for (int k = 0; k < hs.size(); k++) {
                    Sweeper.Hint h = hs.get(k);
                    if (k > 0) sb.append(',');
                    sb.append('[').append(h.i).append(",\"").append(h.key).append("\",").append(h.lb).append(']');
                }
                sb.append("]}");
                System.out.println(sb);
            }
        }
    }
}
