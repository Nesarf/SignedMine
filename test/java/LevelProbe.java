// Every difficulty in Sweeper.LEVELS, dealt from the Java side.
//   javac -encoding UTF-8 -cp build-java -d build-java test/java/LevelProbe.java
//   java -cp build-java LevelProbe [rounds]
//
// The README's "each level dealt N of N" table used to rest on the JS engine's numbers.
// Java is the本体, so it needs its own pass; the largest boards take a few seconds each.
import java.util.*;

public class LevelProbe {
    public static void main(String[] args) {
        int rounds = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        Random rnd = new Random(20261122);
        int totalFail = 0;
        int slowest = -1;
        double slowestMs = 0;
        for (int i = 0; i < Sweeper.LEVELS.length; i++) {
            int[] lv = Sweeper.LEVELS[i];
            int R = lv[0], C = lv[1], M = lv[2];
            int ok = 0, fail = 0;
            long t0 = System.nanoTime();
            for (int k = 0; k < rounds; k++) {
                if (Sweeper.newGame(R, C, M, rnd.nextInt(1 << 20))) ok++; else fail++;
            }
            double avg = (System.nanoTime() - t0) / 1e6 / rounds;
            totalFail += fail;
            if (avg > slowestMs) { slowestMs = avg; slowest = i; }
            System.out.printf("%2d. %-13s %2d x %2d  地块 %4d  密度 %4.1f%%  失败 %d/%d  平均 %6.0f ms%n",
                    i + 1, Sweeper.LEVEL_NAMES[i], R, C, R * C, M * 100.0 / (R * C), fail, rounds, avg);
        }
        System.out.println();
        System.out.printf("合计 %d 档，失败 %d 次；最慢是第 %d 档（%s，平均 %.0f ms）%n",
                Sweeper.LEVELS.length, totalFail, slowest + 1,
                Sweeper.LEVEL_NAMES[slowest], slowestMs);
        System.out.println(totalFail == 0 ? "全部可发牌" : "有档位发不出来");
    }
}
