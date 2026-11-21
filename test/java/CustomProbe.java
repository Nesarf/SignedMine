// Custom difficulty: does start() take arbitrary sizes, and does the level box settle correctly?
//   javac -encoding UTF-8 -cp build-java -d build-java test/java/CustomProbe.java
//   java -cp build-java CustomProbe
public class CustomProbe {
    static int bad = 0;
    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (detail.isEmpty() ? "" : "  " + detail));
        if (!ok) bad++;
    }

    public static void main(String[] args) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            Sweeper f = new Sweeper();

            // 1) 起始的固定档
            check("初始是默认档", Sweeper.R == 16 && Sweeper.C == 30 && Sweeper.M == 100,
                    Sweeper.R + "x" + Sweeper.C + "/" + Sweeper.M);
            check("下拉框停在默认档", f.levelBox.getSelectedIndex() == Sweeper.DEFAULT_LEVEL,
                    "索引 " + f.levelBox.getSelectedIndex());

            // 2) 自定义尺寸（不在固定表里）
            f.start(7, 11, 12);
            check("自定义尺寸生效", Sweeper.R == 7 && Sweeper.C == 11 && Sweeper.M == 12,
                    Sweeper.R + "x" + Sweeper.C + "/" + Sweeper.M);
            check("盘面跟着变", Sweeper.board.R == 7 && Sweeper.board.C == 11 && Sweeper.board.M == 12, "");
            check("发得出牌", Sweeper.board.revCount() >= 1, "已翻开 " + Sweeper.board.revCount());

            // 3) 自定义尺寸不在表里，拨回默认
            f.syncLevelBox();
            check("非标准尺寸回落到默认档", f.levelBox.getSelectedIndex() == Sweeper.DEFAULT_LEVEL,
                    "索引 " + f.levelBox.getSelectedIndex());
            check("回落不触发新局", Sweeper.R == 7 && Sweeper.C == 11,
                    Sweeper.R + "x" + Sweeper.C);

            // 4) 落在固定档上时拨回该项
            f.start(9, 9, 10);
            f.syncLevelBox();
            check("标准尺寸对上原档", f.levelBox.getSelectedIndex() == 4,
                    "索引 " + f.levelBox.getSelectedIndex());

            // 5) 下拉框里多了「自定义…」一项
            check("下拉框多一项", f.levelBox.getItemCount() == Sweeper.LEVELS.length + 1,
                    f.levelBox.getItemCount() + " 项");

            // 6) 大盘自定义（3000 格，密度 18%）
            f.start(60, 50, 540);
            check("3000 格也能发", Sweeper.board.revCount() >= 1, "已翻开 " + Sweeper.board.revCount());
        });
        System.out.println(bad == 0 ? "自定义难度全部通过" : bad + " 项不通过");
        System.exit(bad == 0 ? 0 : 1);
    }
}
