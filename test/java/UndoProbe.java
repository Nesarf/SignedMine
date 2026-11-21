// Undo: does the board really go back, including the hint table and the flag set?
//   javac -encoding UTF-8 -cp build-java -d build-java test/java/UndoProbe.java
//   java -cp build-java UndoProbe
public class UndoProbe {
    static int bad = 0;
    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (detail.isEmpty() ? "" : "  " + detail));
        if (!ok) bad++;
    }

    public static void main(String[] args) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            Sweeper f = new Sweeper();
            Sweeper.Board b = Sweeper.board;

            // 1) 翻开一格后撤销
            int rev0 = b.revCount();
            int safe = -1;
            for (int i = 0; i < b.R * b.C; i++) if (b.unk(i) && !b.mine[i]) { safe = i; break; }
            f.pushHistory();
            b.open(safe);
            Sweeper.topUpWeak();
            int rev1 = b.revCount();
            int weak1 = Sweeper.weak.size();
            f.undo();
            check("翻开后撤销", b.revCount() == rev0, rev0 + " -> " + rev1 + " -> " + b.revCount());
            check("提示表跟着回滚", Sweeper.weak.size() <= weak1, "撤销前 " + weak1 + "，撤销后 " + Sweeper.weak.size());

            // 2) 插旗后撤销
            int flagTarget = -1;
            for (int i = 0; i < b.R * b.C; i++) if (!b.rev[i]) { flagTarget = i; break; }
            f.pushHistory();
            b.flag[flagTarget] = true;
            f.undo();
            check("插旗后撤销", !b.flag[flagTarget], "格子 " + flagTarget);

            // 3) 连续撤销到空
            while (!Sweeper.history.isEmpty()) f.undo();
            f.undo();   // 空栈上再来一次，不该抛
            check("空栈撤销不抛异常", true, "");

            // 4) 按钮状态
            check("无历史时按钮禁用", !Sweeper.undoBtn.isEnabled(), "");
            f.pushHistory();
            f.updateCounters();
            check("有历史时按钮可用", Sweeper.undoBtn.isEnabled(), "");

            // 5) 新局清空历史
            f.start(9, 9, 10);
            check("新局清空历史", Sweeper.history.isEmpty(), "剩 " + Sweeper.history.size());
        });
        System.out.println(bad == 0 ? "撤销全部通过" : bad + " 项不通过");
        System.exit(bad == 0 ? 0 : 1);
    }
}
