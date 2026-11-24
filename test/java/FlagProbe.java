// Flags must never change a hint.
//   javac -encoding UTF-8 -cp build-java -d build-java test/java/FlagProbe.java
//   java -cp build-java FlagProbe
//
// If a hint responded to flags, a player could plant one flag and watch the
// number to learn whether that cell is a mine - the hint becomes an oracle and
// the game degenerates into trial and error. So this probe records every hint,
// plants flags, and requires the hints to be identical.
public class FlagProbe {
    static int bad = 0;
    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (detail.isEmpty() ? "" : "  " + detail));
        if (!ok) bad++;
    }

    public static void main(String[] args) throws Exception {
        final int[] cellsChecked = {0};
        final int[] diffs = {0};
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            Sweeper f = new Sweeper();
            for (int round = 0; round < 8; round++) {
                f.start(16, 30, 100);
                Sweeper.Board b = Sweeper.board;
                int N = b.R * b.C;

                // 插旗前：记录每个已翻开数字格的提示
                Sweeper.Hint[] before = new Sweeper.Hint[N];
                for (int q = 0; q < N; q++) {
                    if (b.rev[q] && b.num(q) > 0) before[q] = b.hintFor(q, new int[Sweeper.NK], true);
                }

                // 随便插一批旗（真假混合，正是最容易被试探的情形）
                int planted = 0;
                for (int q = 0; q < N && planted < 12; q++) {
                    if (!b.rev[q] && !b.flag[q]) { b.flag[q] = true; planted++; }
                }

                // 插旗后再算，必须逐项相同
                for (int q = 0; q < N; q++) {
                    if (!(b.rev[q] && b.num(q) > 0)) continue;
                    cellsChecked[0]++;
                    Sweeper.Hint a = before[q];
                    Sweeper.Hint c = b.hintFor(q, new int[Sweeper.NK], true);
                    boolean same = (a == null && c == null)
                            || (a != null && c != null && a.key.equals(c.key) && a.lb == c.lb && a.exact == c.exact);
                    if (!same) diffs[0]++;
                }
            }
        });
        check("插旗不改变任何提示", diffs[0] == 0,
                "检查 " + cellsChecked[0] + " 个数字格，不同 " + diffs[0] + " 处");
        System.out.println(bad == 0 ? "旗与提示完全隔离" : bad + " 项不通过");
        System.exit(bad == 0 ? 0 : 1);
    }
}
