import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;
import java.util.prefs.Preferences;

public class Sweeper extends JFrame {
    static final Map<String,int[]> SHAPES = new LinkedHashMap<>();
    static final Map<String,String> DISP = new LinkedHashMap<>();
    static final int[][] OFF = {{0,0},{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
    // ± / ∓ 的格子按符号笔画取，不是「上下两整行」：
    //   ± = 加号（2-4-5-7）在上、减号（6-7-8）在下，并起来 245678；
    //   ∓ = 上下镜像：减号（1-2-3）在上、加号（2-4-5-7）在下，即 123457。
    // 与 lib/core.js 的 SHAPES 必须逐项一致，改一处就要改另一处。
    static {
        SHAPES.put("up",new int[]{1,2,3});        DISP.put("up","↑");
        SHAPES.put("down",new int[]{6,7,8});      DISP.put("down","↓");
        SHAPES.put("left",new int[]{1,4,6});      DISP.put("left","←");
        SHAPES.put("right",new int[]{3,5,8});     DISP.put("right","→");
        SHAPES.put("ul",new int[]{1,2,4});        DISP.put("ul","↖");
        SHAPES.put("ur",new int[]{2,3,5});        DISP.put("ur","↗");
        SHAPES.put("dl",new int[]{4,6,7});        DISP.put("dl","↙");
        SHAPES.put("dr",new int[]{5,7,8});        DISP.put("dr","↘");
        SHAPES.put("cross",new int[]{1,3,6,8});   DISP.put("cross","×");
        SHAPES.put("sq",new int[]{3,4,7});        DISP.put("sq","√");
        SHAPES.put("mu",new int[]{1,3,4,5,6});    DISP.put("mu","μ");
        SHAPES.put("integ",new int[]{2,3,6,7});   DISP.put("integ","∫");
        SHAPES.put("vert",new int[]{2,7});        DISP.put("vert","∣");
        SHAPES.put("dash",new int[]{4,5});        DISP.put("dash","—");
        SHAPES.put("bs",new int[]{1,8});          DISP.put("bs","╲");
        SHAPES.put("fs",new int[]{3,6});          DISP.put("fs","╱");
        SHAPES.put("neq",new int[]{2,4,5,7});     DISP.put("neq","≠");
        SHAPES.put("pm",new int[]{2,4,5,6,7,8});  DISP.put("pm","±");
        SHAPES.put("mp",new int[]{1,2,3,4,5,7});  DISP.put("mp","∓");
        SHAPES.put("corner",new int[]{1,2,3,4});  DISP.put("corner","⌐");
        SHAPES.put("tf",new int[]{2,6,8});        DISP.put("tf","∴");
        SHAPES.put("bf",new int[]{1,3,7});        DISP.put("bf","∵");
        SHAPES.put("ang",new int[]{3,6,8});       DISP.put("ang","∠");
        SHAPES.put("lt",new int[]{2,4,7});        DISP.put("lt","〈");
        SHAPES.put("gt",new int[]{2,5,7});        DISP.put("gt","〉");
    }

    static class Hint { int i; String key; int lb, n; boolean exact = true; }

    // 形状键的稳定顺序 = 上面 SHAPES 的插入顺序。预展开成数组，
    // 热路径（每格 25 个形状 × 每个形状若干次邻居遍历）不再走 Map 的字符串查找。
    static final String[] KEYS;
    static final int NK;
    static final HashMap<String,Integer> KEY_INDEX = new HashMap<>();
    static {
        KEYS = SHAPES.keySet().toArray(new String[0]);
        NK = KEYS.length;
        for (int k = 0; k < NK; k++) KEY_INDEX.put(KEYS[k], k);
    }
    static int keyIndex(String key) {
        Integer k = KEY_INDEX.get(key);
        return k == null ? -1 : k;
    }
    static boolean contains(int[] arr, int v) {
        for (int x : arr) if (x == v) return true;
        return false;
    }

    // 与 lib/core.js 的 makeRng 同一支算法（mulberry32），32 位溢出语义逐位对齐。
    // 这样同一个 seed 在 Java 与 JS 里发出同一局，两边才能交叉验证而不是各测各的。
    static final class Mulberry32 {
        private int a;
        Mulberry32(int seed){ a = seed; }
        double next() {
            a += 0x6D2B79F5;
            int t = a;
            t = (t ^ (t >>> 15)) * (t | 1);
            t ^= t + (t ^ (t >>> 7)) * (t | 61);
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
        }
    }

    static class Board {
        int R, C, M, seed;
        boolean[] mine, rev, known, flag;
        boolean placed, dead;
        int[] num;
        Mulberry32 rng;
        boolean[] snapshot;        // 开局中心格翻开后的局面，发完牌要退回来
        int revN = 0;              // 已翻开格数：revCount 由每次 O(R*C) 变成 O(1)
        int[][] NB;                // 邻域表，构造时算好（旧版每次 nbs() 都 new 一个 ArrayList）
        int[][] SC;                // 形状格缓存，按 (格, 形状) 惰性填充
        final ArrayList<Integer> openedBuf = new ArrayList<>();  // 最近一次 open 新开的格

        Board(int R, int C, int M, int seed) {
            this.R = R; this.C = C; this.M = M; this.seed = seed;
            int n = R * C;
            mine = new boolean[n]; rev = new boolean[n]; known = new boolean[n]; flag = new boolean[n];
            num = new int[n];
            rng = new Mulberry32(seed);
            NB = new int[n][];
            for (int r = 0; r < R; r++) for (int c = 0; c < C; c++) {
                int[] tmp = new int[8];
                int k = 0;
                for (int d = 1; d <= 8; d++) {
                    int rr = r + OFF[d][0], cc = c + OFF[d][1];
                    if (rr >= 0 && rr < R && cc >= 0 && cc < C) tmp[k++] = rr * C + cc;
                }
                NB[r * C + c] = Arrays.copyOf(tmp, k);
            }
            SC = new int[n * NK][];
        }
        int I(int r, int c){ return r*C + c; }
        boolean inside(int r, int c){ return r >= 0 && r < R && c >= 0 && c < C; }
        int[] nbs(int i){ return NB[i]; }

        void place(int safe) {
            int sr = safe / C, sc = safe % C;
            int[] pool = new int[R * C];
            int k = 0;
            for (int r = 0; r < R; r++)
                for (int c = 0; c < C; c++)
                    if (Math.max(Math.abs(r - sr), Math.abs(c - sc)) > 1) pool[k++] = I(r, c);
            // 与 lib/core.js 同一套洗牌：倒序 Fisher-Yates，j = floor(rng * (i+1))
            for (int i = k - 1; i > 0; i--) {
                int j = (int) (rng.next() * (i + 1));
                int t = pool[i]; pool[i] = pool[j]; pool[j] = t;
            }
            for (int t = 0; t < M; t++) mine[pool[t]] = true;
            placed = true;
            Arrays.fill(num, -1);
        }
        int num(int i) {
            if (num[i] >= 0) return num[i];
            int n = 0;
            for (int j : NB[i]) if (mine[j]) n++;
            num[i] = n;
            return n;
        }
        void open(int i) {
            openedBuf.clear();
            if (rev[i]) return;
            if (!placed) place(i);
            if (mine[i]) { dead = true; rev[i] = true; revN++; openedBuf.add(i); return; }
            ArrayDeque<Integer> st = new ArrayDeque<>();
            st.push(i);
            while (!st.isEmpty()) {
                int p = st.pop();
                if (rev[p] || mine[p]) continue;
                rev[p] = true; revN++; openedBuf.add(p);
                if (num(p) == 0) for (int q : NB[p]) if (!rev[q]) st.push(q);
            }
        }
        boolean unk(int i){ return !rev[i] && !known[i]; }
        int rest(int i){ int n = num(i); for (int j : NB[i]) if (known[j]) n--; return n; }
        int revCount(){ return revN; }

        boolean[] localMine = new boolean[0], localSafe = new boolean[0];
        void ensureLocal() {
            if (localMine.length != R * C) { localMine = new boolean[R * C]; localSafe = new boolean[R * C]; }
            else { Arrays.fill(localMine, false); Arrays.fill(localSafe, false); }
        }
        void localRules() {
            ensureLocal();
            for (int q = 0; q < R*C; q++) {
                if (!rev[q]) continue;
                int v = num(q);
                if (v == 0) continue;
                int[] nb = NB[q];
                int hidden = 0;
                for (int p : nb) if (unk(p)) hidden++;
                if (hidden == 0) continue;
                int r = rest(q);
                if (r == 0) { for (int p : nb) if (unk(p)) localSafe[p] = true; }
                else if (r == hidden) { for (int p : nb) if (unk(p)) localMine[p] = true; }
            }
        }
        void subsetRules() {
            ensureLocal();
            HashMap<Integer, HashSet<Integer>> hmap = new HashMap<>();
            HashMap<Integer, Integer> rmap = new HashMap<>();
            for (int q = 0; q < R*C; q++) {
                if (!rev[q] || num(q) == 0) continue;
                HashSet<Integer> h = new HashSet<>();
                for (int p : NB[q]) if (unk(p)) h.add(p);
                if (!h.isEmpty()) { hmap.put(q, h); rmap.put(q, rest(q)); }
            }
            for (int a : hmap.keySet()) {
                HashSet<Integer> ha = hmap.get(a);
                int ar = a / C, ac = a % C;
                for (int dr = -2; dr <= 2; dr++) for (int dc = -2; dc <= 2; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    if (!inside(ar+dr, ac+dc)) continue;
                    int b = I(ar+dr, ac+dc);
                    HashSet<Integer> hb = hmap.get(b);
                    if (hb == null) continue;
                    HashSet<Integer> small, big; int d;
                    if (ha.size() < hb.size() && hb.containsAll(ha)) { small = ha; big = hb; d = rmap.get(b) - rmap.get(a); }
                    else if (hb.size() < ha.size() && ha.containsAll(hb)) { small = hb; big = ha; d = rmap.get(a) - rmap.get(b); }
                    else continue;
                    ArrayList<Integer> diff = new ArrayList<>();
                    for (int x : big) if (!small.contains(x)) diff.add(x);
                    if (diff.isEmpty()) continue;
                    if (d == diff.size()) for (int x : diff) localMine[x] = true;
                    else if (d == 0) for (int x : diff) localSafe[x] = true;
                }
            }
        }
        int[] shapeCellsK(int q, int k) {
            int at = q * NK + k;
            int[] hit = SC[at];
            if (hit != null) return hit;
            int r = q / C, c = q % C;
            int[] ids = SHAPES.get(KEYS[k]);
            int[] tmp = new int[ids.length];
            int m = 0;
            for (int id : ids) {
                int rr = r + OFF[id][0], cc = c + OFF[id][1];
                if (rr >= 0 && rr < R && cc >= 0 && cc < C) tmp[m++] = rr * C + cc;
            }
            int[] out = Arrays.copyOf(tmp, m);
            SC[at] = out;
            return out;
        }
        // 提示约束参与联合推理。提示给的是「形状内雷数 ∈ [lo, hi]」，局部约束是
        // 「邻域内雷数 = rest」；两者做子集推理就能推出差集（A ⊂ B 时 B\A 的区间是
        // [lo_B - hi_A, hi_B - lo_A]）。旧版只拿局部约束互相比，提示给的区间没参与，
        // 所以 2500 格以上的盘面在 18% 密度上都发不出来——补上这条之后单次成功率从 0% 到 27%。
        void hintSubsetRules(java.util.List<Hint> hints) {
            ensureLocal();
            if (hints == null || hints.isEmpty()) return;
            int N = R * C;
            int[][] locCells = new int[N][];
            int[] locLo = new int[N], locHi = new int[N];
            boolean[] hasLoc = new boolean[N];
            for (int q = 0; q < N; q++) {
                if (!rev[q] || num(q) == 0) continue;
                int h = 0;
                for (int p : NB[q]) if (unk(p)) h++;
                if (h == 0) continue;
                int[] cells = new int[h];
                int t = 0;
                for (int p : NB[q]) if (unk(p)) cells[t++] = p;
                locCells[q] = cells;
                locLo[q] = rest(q); locHi[q] = rest(q);
                hasLoc[q] = true;
            }
            for (Hint hh : hints) {
                int k = keyIndex(hh.key);
                if (k < 0) continue;
                int[] all = shapeCellsK(hh.i, k);
                int un = 0;
                for (int p : all) if (unk(p)) un++;
                if (un == 0) continue;
                int[] cells = new int[un];
                int t = 0;
                for (int p : all) if (unk(p)) cells[t++] = p;
                int base = rest(hh.i);
                int outside = 0;
                for (int p : NB[hh.i]) if (unk(p) && !contains(all, p)) outside++;
                int lo = Math.max(0, base - outside), hi = base;
                int ar = hh.i / C, ac = hh.i % C;
                for (int dr = -2; dr <= 2; dr++) for (int dc = -2; dc <= 2; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    if (!inside(ar + dr, ac + dc)) continue;
                    int bq = I(ar + dr, ac + dc);
                    if (!hasLoc[bq]) continue;
                    subsetPush(cells, lo, hi, locCells[bq], locLo[bq], locHi[bq]);
                    subsetPush(locCells[bq], locLo[bq], locHi[bq], cells, lo, hi);
                }
            }
        }
        // small 的格集合若是 big 的子集，就把差集推出来
        void subsetPush(int[] sc, int slo, int shi, int[] bc, int blo, int bhi) {
            if (sc.length >= bc.length) return;
            for (int x : sc) if (!contains(bc, x)) return;
            int dl = 0;
            int[] diff = new int[bc.length - sc.length];
            for (int x : bc) if (!contains(sc, x)) diff[dl++] = x;
            if (dl == 0) return;
            int lo = Math.max(0, blo - shi), hi = bhi - slo;
            if (hi <= 0) { for (int t = 0; t < dl; t++) localSafe[diff[t]] = true; }
            else if (lo >= dl) { for (int t = 0; t < dl; t++) localMine[diff[t]] = true; }
        }
        ArrayList<Integer> shapeCells(int q, String key) {
            int[] a = shapeCellsK(q, keyIndex(key));
            ArrayList<Integer> out = new ArrayList<>(a.length);
            for (int v : a) out.add(v);
            return out;
        }

        // 形状提示的上下界。
        // 形状内雷数 R_in = base - R_out，其中 base 是中心格的剩余雷数、R_out 是形状外未开邻格里的雷数。
        //   上界：R_out >= 0，所以 R_in <= base。
        //   下界一：R_out <= 形状外未开格数，所以 R_in >= base - outside。
        //   下界二：对每个已翻开的邻居 q2，它的雷可能落在「形状内」「形状外」「两边都不在」，
        //           所以 R_in >= rest(q2) - (q2 的未开邻格里不在形状内的个数)。
        // 旧版把 rest(q2) - inner 直接当成「形状外雷数下界」，于是把「两边都不在」的那些格子
        // 算进了形状外，上界被压低到与下界相等，判出假的「精确值」——玩家照着推理会错。
        // 判定：lb == base 才算精确（此时形状内恰好 base 颗）。
        // force=true 时，下界为 0、暂无可推结论的形状也保留（绘制时只画淡色符号），
        // 这样每个数字格从发牌那一刻起就有符号，不会等周围翻开才冒出来。
        Hint hintFor(int q, int[] used, boolean force) {
            if (!rev[q]) return null;
            int v = num(q);
            if (v == 0) return null;
            int base = rest(q);
            int[] nb = NB[q];
            // 旗子只是玩家自己的标记，不参与任何计算：这里的「未开」一律指未翻开，
            // 插旗与否不影响符号与数字。只有求解标记（known）才算已知的雷。
            Hint best = null;
            int bestScore = Integer.MIN_VALUE;
            for (int k = 0; k < NK; k++) {
                int[] cells = shapeCellsK(q, k);
                int un = 0;
                for (int p : cells) if (!rev[p]) un++;
                if (un == 0) continue;
                int outside = 0;
                for (int p : nb) if (!rev[p] && !contains(cells, p)) outside++;
                int lb = Math.max(0, base - outside);
                for (int q2 : nb) {
                    if (!rev[q2] || num(q2) == 0) continue;
                    int inner = 0, unkQ2 = 0;
                    for (int p : NB[q2]) {
                        if (rev[p]) continue;
                        unkQ2++;
                        if (contains(cells, p)) inner++;
                    }
                    int lower = rest(q2) - (unkQ2 - inner);
                    if (lower > lb) lb = lower;
                }
                if (lb > un) lb = un;                      // 形状内雷数不可能超过未开格数
                boolean exact = (lb == base);
                if (!exact && lb < 1 && !force) continue;   // 下界为 0 的形状默认不生成，force 时保留
                int score = (exact ? 100 : 0) + lb * 10 - un - (used == null ? 0 : used[k]);
                if (score > bestScore) {
                    bestScore = score;
                    best = new Hint();
                    best.i = q; best.key = KEYS[k]; best.lb = lb; best.n = un; best.exact = exact;
                }
            }
            return best;
        }

        // 整表重算（新局、主动刷新、探测用）；翻格后的增量补算走顶层 topUpWeak()。
        ArrayList<Hint> weakHints() {
            ArrayList<Hint> out = new ArrayList<>();
            int[] used = new int[NK];
            for (int q = 0; q < R*C; q++) {
                Hint h = hintFor(q, used, true);
                if (h != null) { out.add(h); used[keyIndex(h.key)]++; }
            }
            return out;
        }

        void hintRules(java.util.List<Hint> hints) {            ensureLocal();
            for (Hint h : hints) {
                int[] all = shapeCellsK(h.i, keyIndex(h.key));
                int un = 0;
                for (int p : all) if (unk(p)) un++;
                if (un == 0) continue;
                int base = rest(h.i);
                // 剩余雷数为 0：形状内一颗都没有，全安全（严格结论）。
                if (base <= 0) { for (int p : all) if (unk(p)) localSafe[p] = true; continue; }
                // 否则只有「下界够到未开格数」才能说全雷。
                int outside = 0;
                for (int p : NB[h.i]) if (unk(p) && !contains(all, p)) outside++;
                if (base - outside >= un) { for (int p : all) if (unk(p)) localMine[p] = true; }
            }
        }
        boolean solve(java.util.List<Hint> hints) {
            int total = R*C - M, guard = 0;
            while (revN < total) {
                if (guard++ > 20000) return false;
                localRules();
                boolean any = has(localMine) || has(localSafe);
                if (!any) { subsetRules(); any = has(localMine) || has(localSafe); }
                if (!any) { hintSubsetRules(hints); any = has(localMine) || has(localSafe); }
                if (!any) { hintRules(hints); any = has(localMine) || has(localSafe); }
                if (!any) {
                    int k = 0; for (boolean b : known) if (b) k++;
                    if (k >= M) for (int i = 0; i < R*C; i++) if (unk(i)) localSafe[i] = true;
                }
                boolean progress = false;
                for (int i = 0; i < R*C; i++) if (localMine[i] && unk(i)) { known[i] = true; progress = true; }
                for (int i = 0; i < R*C; i++) if (localSafe[i] && unk(i)) { if (mine[i]) return false; open(i); progress = true; }
                if (!progress) return false;
            }
            for (int i = 0; i < R*C; i++) if (mine[i]) known[i] = true;
            return !dead;
        }
        boolean has(boolean[] a){ for (boolean b : a) if (b) return true; return false; }

        // 候选提示，只收能推进局面的两种：
        //   下界够到未开格数 → 形状内全是雷
        //   剩余雷数为 0 → 形状内全安全
        // 旧版还会收「上界 <= 0」的一批 lb=0 条目，那批在 hintRules 里被当成全安全，
        // 等于每轮朝雷上开，高级档发牌反复空转到八十轮（与网页版旧版同一个错）。
        ArrayList<Hint> candidates() {
            ArrayList<Hint> out = new ArrayList<>();
            for (int q = 0; q < R*C; q++) {
                if (!rev[q] || num(q) == 0) continue;
                int base = rest(q);
                int[] nb = NB[q];
                for (int k = 0; k < NK; k++) {
                    int[] cells = shapeCellsK(q, k);
                    int un = 0;
                    for (int p : cells) if (unk(p)) un++;
                    if (un == 0) continue;
                    int outside = 0;
                    for (int p : nb) if (unk(p) && !contains(cells, p)) outside++;
                    int lb = Math.max(0, base - outside);
                    for (int q2 : nb) {
                        if (!rev[q2] || num(q2) == 0) continue;
                        int inner = 0, unkQ2 = 0;
                        for (int p : NB[q2]) {
                            if (!unk(p)) continue;
                            unkQ2++;
                            if (contains(cells, p)) inner++;
                        }
                        int lower = rest(q2) - (unkQ2 - inner);
                        if (lower > lb) lb = lower;
                    }
                    if (lb > un) lb = un;
                    // 只要约束还有内容就收：下界非零，或上界没满。
                    // 旧条件只收「lb 够到格数」或「base 为 0」，开局时几乎不存在，候选池会空掉。
                    if (lb >= 1 || base < un) {
                        Hint h = new Hint();
                        h.i = q; h.key = KEYS[k]; h.lb = Math.max(0, lb); h.n = un;
                        out.add(h);
                    }
                }
            }
            return out;
        }
    }

    // ---------- 生成 ----------
    static Board board; static ArrayList<Hint> hints = new ArrayList<>(); static ArrayList<Hint> weak = new ArrayList<>();
    static Hint[] weakByCell;

    // ---- 撤销 ----
    // 每一步改动局面之前把 rev/known/flag 与提示表整个存一份。
    // 提示也要一起回滚：撤销之后那一格可能又变成未翻开，提示不该还挂着。
    static final int HISTORY_LIMIT = 200;
    static final class Snapshot {
        boolean[] rev, known, flag;
        int revN;
        boolean dead;
        Hint[] weakByCell;
    }
    static final ArrayDeque<Snapshot> history = new ArrayDeque<>();
    static JButton undoBtn;          // 无历史时禁用
    static boolean[] snapshot;
    static int R = 16, C = 30, M = 100;
    static String mode = "高级 16×30 · 100 雷";

    // 24 档难度。小盘密度可到 21%，但 2500 格以上的三档必须压在 18% 以下：
    // 实测 40×63·500（19.8%）单次 build 成功率是 0/60，240 次重试也出不来；
    // 降到 18% 后单次 6%~28%，重试兜得住。密度再往 23% 走，小盘也会掉到一半以下。
    static final int[][] LEVELS = {
        {5, 5, 3}, {6, 6, 5}, {7, 7, 7}, {8, 8, 9}, {9, 9, 10}, {10, 10, 14},
        {12, 12, 20}, {12, 12, 26}, {14, 14, 30}, {16, 16, 40}, {16, 16, 51}, {16, 20, 60},
        {16, 24, 70}, {16, 30, 99}, {16, 30, 100}, {20, 20, 80}, {20, 30, 120}, {24, 30, 145},
        {24, 36, 170}, {30, 30, 190}, {30, 40, 240}, {40, 63, 450}, {50, 50, 450}, {60, 50, 540}
    };
    static final String[] LEVEL_NAMES = {
        "5×5 · 3", "6×6 · 5", "7×7 · 7", "8×8 · 9", "9×9 · 10", "10×10 · 14",
        "12×12 · 20", "12×12 · 26", "14×14 · 30", "16×16 · 40", "16×16 · 51", "16×20 · 60",
        "16×24 · 70", "16×30 · 99", "16×30 · 100", "20×20 · 80", "20×30 · 120", "24×30 · 145",
        "24×36 · 170", "30×30 · 190", "30×40 · 240", "40×63 · 450", "50×50 · 450", "60×50 · 540"
    };
    static final int DEFAULT_LEVEL = 14;          // 16×30 · 100

    // 整表重算（新局 / 主动刷新时用）
    static void refreshAllWeak() {
        weakByCell = new Hint[R * C];
        weak = new ArrayList<>();
        int[] used = new int[NK];
        for (int q = 0; q < R * C; q++) {
            Hint h = board.hintFor(q, used, true);
            if (h != null) { weakByCell[q] = h; weak.add(h); used[keyIndex(h.key)]++; }
        }
    }

    // 增量补算：只碰刚翻开的格与它们的邻居。
    // 提示本来就是逐格独立算出来的，所以增量成立；旧版每次翻格都调一次全盘 weakHints。
    static void topUpWeak() {
        if (board.openedBuf.isEmpty()) return;
        int[] used = new int[NK];
        for (int q = 0; q < R * C; q++) if (weakByCell[q] != null) used[keyIndex(weakByCell[q].key)]++;
        boolean added = false;
        for (int p : board.openedBuf) {
            if (addWeak(p, used)) added = true;
            for (int q : board.nbs(p)) if (addWeak(q, used)) added = true;
        }
        if (added) {
            weak = new ArrayList<>();
            for (int q = 0; q < R * C; q++) if (weakByCell[q] != null) weak.add(weakByCell[q]);
        }
    }
    static boolean addWeak(int q, int[] used) {
        if (q < 0 || q >= R * C || weakByCell[q] != null) return false;
        Hint h = board.hintFor(q, used, true);
        if (h == null) return false;
        weakByCell[q] = h; used[keyIndex(h.key)]++;
        return true;
    }

    // 单次发牌，不做外层重试。lib/core.js 的 build() 与它逐位等价，
    // 交叉验证（test/java/CrossProbe.java 对 test/cross.mjs）拿它当基准。
    // 返回 null 表示这局加满提示也解不开；成功时 b.snapshot 是中心格翻开后的局面。
    static Board buildOnce(int rows, int cols, int mines, int seed, ArrayList<Hint> hintsOut) {
        Board b = new Board(rows, cols, mines, seed);
        b.open(b.I(rows/2, cols/2));
        b.snapshot = b.rev.clone();
        ArrayList<Hint> hs = new ArrayList<>();
        HashSet<Integer> usedKey = new HashSet<>();
        boolean ok = false;
        int stall = 0;
        for (int k = 0; k < 80; k++) {
            int revBefore = b.revN, knownBefore = 0;
            for (boolean x : b.known) if (x) knownBefore++;
            if (b.solve(hs)) { ok = true; break; }
            int knownAfter = 0;
            for (boolean x : b.known) if (x) knownAfter++;
            // 加了提示还是没有任何推进，这局基本走不通，别再空转到八十轮
            stall = (b.revN == revBefore && knownAfter == knownBefore) ? stall + 1 : 0;
            if (stall >= 6) break;
            // 每轮加一批而不是一条：大盘开局单条提示推不动局面，
            // 一条一条地加，八十轮也凑不出足够强的约束。
            ArrayList<Hint> cands = b.candidates();
            cands.sort((x, y) -> y.n - x.n);
            int added = 0;
            for (Hint c : cands) {
                if (added >= 8) break;
                int key = c.i * 64 + keyIndex(c.key);
                if (usedKey.contains(key)) continue;
                hs.add(c); usedKey.add(key); added++;
            }
            if (added == 0) break;
        }
        hintsOut.clear();
        hintsOut.addAll(hs);
        return ok ? b : null;
    }

    static boolean newGame(int rows, int cols, int mines, int seed) {
        // 单次 build 的成功率：高级档约 2%，失败局现在很早就能判定，
        // 所以重试次数给足（200 局发牌零失败），代价只是极少数局面多等一会儿。
        for (int t = 0; t < 240; t++) {
            ArrayList<Hint> hs = new ArrayList<>();
            Board b = buildOnce(rows, cols, mines, seed + t * 7919, hs);
            if (b == null) continue;
            board = b; hints = hs; snapshot = b.snapshot;
            R = rows; C = cols; M = mines;
            board.rev = b.snapshot.clone();
            Arrays.fill(board.known, false);
            board.revN = 0;
            for (boolean x : board.rev) if (x) board.revN++;
            for (int i = 0; i < R*C; i++) { board.flag[i] = false; board.num[i] = -1; }
            board.dead = false;
            refreshAllWeak();
            return true;
        }
        return false;
    }

    final BoardPanel panel = new BoardPanel();
    final JLabel status = new JLabel(" ");
    final JLabel mineLabel = new JLabel(" ");
    final JLabel timeLabel = new JLabel("00:00");
    final JCheckBox hintBox = new JCheckBox("显示形状提示", true);
    final JComboBox<String> densityBox;          // 提成字段，截图入口可以按档位设置它
    final JComboBox<String> levelBox;            // 同上，难度
    boolean syncingLevel = false;                // 程序改下拉框时抑制监听器
    int seconds = 0; boolean running = false; int densityLevel = 1;
    long startedAt = 0; int highlight = -1;
    final javax.swing.Timer clock;

    public Sweeper() {
        super("扫雷 · 形状提示版");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton ng = new JButton("新局");
        ng.addActionListener(e -> { startAgain(); });
        String[] levels = java.util.Arrays.copyOf(LEVEL_NAMES, LEVEL_NAMES.length + 1);
        levels[levels.length - 1] = "自定义…";
        levelBox = new JComboBox<>(levels);
        levelBox.setSelectedIndex(DEFAULT_LEVEL);
        levelBox.addActionListener(e -> {
            if (syncingLevel) return;               // 程序改选项时不要再发一局
            int idx = levelBox.getSelectedIndex();
            if (idx >= 0 && idx < LEVELS.length) start(LEVELS[idx][0], LEVELS[idx][1], LEVELS[idx][2]);
            else if (idx == LEVELS.length) customGame();
        });
        bar.add(new JLabel(" 难度 "));
        bar.add(levelBox);
        bar.addSeparator();
        bar.add(ng);
        undoBtn = new JButton("撤销");
        undoBtn.addActionListener(e -> undo());
        undoBtn.setEnabled(false);
        bar.add(undoBtn);
        bar.addSeparator();
        bar.add(hintBox);
        hintBox.addActionListener(e -> panel.repaint());
        String[] dens = {"精简", "标准", "全部"};
        densityBox = new JComboBox<>(dens);
        densityBox.setSelectedIndex(1);
        densityBox.addActionListener(e -> { densityLevel = densityBox.getSelectedIndex(); panel.repaint(); });
        bar.add(new JLabel(" 提示 "));
        bar.add(densityBox);
        JButton hb = new JButton("提示");
        hb.addActionListener(e -> showHint());
        bar.add(hb);
        bar.add(Box.createHorizontalGlue());
        bar.add(mineLabel);
        bar.addSeparator();
        bar.add(timeLabel);
        bar.add(new JLabel("  "));
        add(bar, BorderLayout.NORTH);
        // 2500 格以上的盘面一屏放不下，放进滚动区；小盘面由 GridBagLayout 居中
        JPanel holder = new JPanel(new GridBagLayout());
        holder.add(panel);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        clock = new javax.swing.Timer(100, e -> {
            if (running) {
                long ms = System.currentTimeMillis() - startedAt;
                timeLabel.setText(String.format("%.1fs", ms / 1000.0));
            }
        });
        clock.start();

        start(LEVELS[DEFAULT_LEVEL][0], LEVELS[DEFAULT_LEVEL][1], LEVELS[DEFAULT_LEVEL][2]);
        setSize(1180, 780);
        setLocationRelativeTo(null);
    }

    void start(int rows, int cols, int mines) {
        R = rows; C = cols; M = mines;
        history.clear();
        seconds = 0; running = true; highlight = -1;
        startedAt = System.currentTimeMillis();
        timeLabel.setText("0.0s");
        if (!newGame(rows, cols, mines, new Random().nextInt(1 << 20))) status.setText(" 生成失败，请再按新局");
        else status.setText(" 左键翻开 · 右键插旗");
        panel.relayout();
        updateCounters();
        revalidate(); repaint();
    }
    void startAgain() { start(R, C, M); }

    // 自定义难度：三个数字，密度上限与固定档一致——22% 以上发牌成功率太低。
    void customGame() {
        JSpinner rs = new JSpinner(new SpinnerNumberModel(Math.max(3, R), 3, 100, 1));
        JSpinner cs = new JSpinner(new SpinnerNumberModel(Math.max(3, C), 3, 100, 1));
        JSpinner ms = new JSpinner(new SpinnerNumberModel(Math.max(1, M), 1, 20000, 1));
        JPanel p = new JPanel(new java.awt.GridLayout(3, 2, 6, 6));
        p.add(new JLabel("行")); p.add(rs);
        p.add(new JLabel("列")); p.add(cs);
        p.add(new JLabel("雷")); p.add(ms);
        int ok = JOptionPane.showConfirmDialog(this, p, "自定义难度", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) { syncLevelBox(); return; }
        int r = (Integer) rs.getValue(), c = (Integer) cs.getValue(), m = (Integer) ms.getValue();
        if (m >= r * c) { status.setText(" 雷数要小于格数"); syncLevelBox(); return; }
        double dens = m * 100.0 / (r * c);
        if (dens > 22) {
            status.setText(String.format(" 密度 %.1f%% 太高，22%% 以上发牌成功率太低", dens));
            syncLevelBox();
            return;
        }
        start(r, c, m);
    }

    // 把下拉框拨回与当前盘面匹配的那一档（自定义取消或输入不合法时用）
    void syncLevelBox() {
        int idx = -1;
        for (int i = 0; i < LEVELS.length; i++) {
            if (LEVELS[i][0] == R && LEVELS[i][1] == C && LEVELS[i][2] == M) { idx = i; break; }
        }
        if (idx < 0) idx = DEFAULT_LEVEL;
        syncingLevel = true;
        levelBox.setSelectedIndex(idx);
        syncingLevel = false;
    }

    // 改动局面之前先把当前状态整份存下来
    void pushHistory() {
        if (board == null) return;
        Snapshot s = new Snapshot();
        s.rev = board.rev.clone();
        s.known = board.known.clone();
        s.flag = board.flag.clone();
        s.revN = board.revN;
        s.dead = board.dead;
        s.weakByCell = weakByCell == null ? null : weakByCell.clone();
        history.addLast(s);
        if (history.size() > HISTORY_LIMIT) history.removeFirst();
    }

    void undo() {
        if (board == null) return;
        if (history.isEmpty()) { status.setText(" 没有可撤销的步骤"); return; }
        Snapshot s = history.removeLast();
        board.rev = s.rev.clone();
        board.known = s.known.clone();
        board.flag = s.flag.clone();
        board.revN = s.revN;
        board.dead = s.dead;
        weakByCell = s.weakByCell == null ? null : s.weakByCell.clone();
        weak = new ArrayList<>();
        if (weakByCell != null) {
            for (int q = 0; q < R*C; q++) if (weakByCell[q] != null) weak.add(weakByCell[q]);
        }
        running = !board.dead;
        panel.repaint();
        updateCounters();
        if (!board.dead) status.setText(" 已撤销一步");
    }

    String bestKey(){ return "best_" + R + "x" + C + "_" + M; }
    // 用明确的节点名，不依赖 userNodeForPackage：默认包会落到 /<unnamed> 这种不好查的路径。
    static Preferences prefs(){ return Preferences.userRoot().node("SignedMine"); }
    double bestSeconds(){
        try { return prefs().getDouble(bestKey(), 0); }
        catch (Exception e) { return 0; }
    }
    void saveBest(double v){
        // putDouble 只写内存，flush 才落盘：不 flush 时进程被强杀就拿不到成绩。
        try {
            Preferences p = prefs();
            p.putDouble(bestKey(), v);
            p.flush();
        } catch (Exception e) { }
    }

    void describeCell(int i) {
        int v = board.num(i);
        if (v == 0) { status.setText(" 空格，周围没有雷"); return; }
        String exactTxt = null, weakTxt = null;
        for (Hint h : hints) if (h.i == i && h.lb >= 1) exactTxt = DISP.get(h.key) + h.lb;
        for (Hint h : weak) if (h.i == i && (h.exact || h.lb >= 1)) weakTxt = DISP.get(h.key) + h.lb;
        String pos = "R" + (i / C + 1) + "C" + (i % C + 1);
        if (exactTxt != null)
            status.setText(" " + pos + " 数字 " + v + "：共有 " + exactTxt + " 颗雷在这个形状里");
        else if (weakTxt != null)
            status.setText(" " + pos + " 数字 " + v + "：至少 " + weakTxt + " 颗雷在这个形状里");
        else
            status.setText(" " + pos + " 数字 " + v + "：这格暂时没有形状提示");
    }

    void showHint() {
        if (board == null || board.dead) return;
        for (Hint h : hints) {
            if (!board.rev[h.i]) continue;
            boolean useful = false;
            for (int p : board.shapeCells(h.i, h.key))
                if (!board.rev[p] && !board.flag[p]) useful = true;
            if (!useful) continue;
            highlight = h.i;
            panel.repaint();
            javax.swing.Timer t = new javax.swing.Timer(2400, ev -> { highlight = -1; panel.repaint(); });
            t.setRepeats(false);
            t.start();
            status.setText(" 提示：R" + (h.i / C + 1) + "C" + (h.i % C + 1) + " 的 " + DISP.get(h.key) + h.lb
                    + "（该形状里至少 " + h.lb + " 颗雷）");
            return;
        }
        status.setText(" 当前没有可用的形状提示");
    }

    void updateCounters() {
        if (board == null) { mineLabel.setText(" "); return; }   // 首次发牌全失败时盘面还不存在
        int flags = 0;
        for (int i = 0; i < R*C; i++) if (board.flag[i]) flags++;
        // weak 里也装着下界为 0 的形状（给「全部」档画淡色符号用），统计时不把它们算进「至少」
        int ex = 0, weakCount = 0;
        for (Hint h : weak) { if (h.exact) ex++; else if (h.lb >= 1) weakCount++; }
        mineLabel.setText("剩余雷 " + (M - flags) + " · 精确 " + ex + " / 至少 " + weakCount + "  ");
        if (board.dead) { status.setText(" 踩到雷了 —— 按新局再来"); running = false; }
        else if (board.revCount() == R*C - M) {
            double sec = (System.currentTimeMillis() - startedAt) / 1000.0;
            double prev = bestSeconds();
            double best = (prev <= 0 || sec < prev) ? sec : prev;
            if (prev <= 0 || sec < prev) saveBest(sec);
            status.setText(String.format(" 清完了 —— %.1f 秒（最佳 %.1f 秒，%d 条提示）", sec, best, hints.size()));
            running = false;
        }
        if (undoBtn != null) undoBtn.setEnabled(!history.isEmpty());
    }

    class BoardPanel extends JPanel {
        int cell = 34, pad = 12;
        int ox = 12, oy = 12;            // 棋盘在面板里的绘制起点（居中）
        BoardPanel() {
            setBackground(new Color(0xE9EEF3));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { hit(e); }
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                        int c = (e.getX() - ox) / cell, r = (e.getY() - oy) / cell;
                        if (board != null && board.inside(r, c)) chord(board.I(r, c));
                    }
                }
            });
            // 窗口一改大小就重排：格距同时受宽和高约束，棋盘跟着长到合适的大小。
            addComponentListener(new ComponentAdapter() {
                public void componentResized(ComponentEvent e) { relayout(); repaint(); }
            });
        }
        void relayout() {
            // 可用横向空间：优先看滚动视口，还没显示时退回设计宽度
            int availW = 1180;
            java.awt.Container p = getParent();
            if (p != null && p.getWidth() > 0) availW = p.getWidth();
            int cw = (availW - 2*pad) / Math.max(C, 1);
            // 下限放到 14：50×50 这种 2500 格的盘面靠滚动看，不靠缩到看不清
            int next = Math.max(14, Math.min(34, cw));
            cell = next;
            ox = pad; oy = pad;
            // 字号跟着格子走：极限档的格子只有二十几 px，固定 17 px 的数字会和小字叠在一起
            numFont = new Font("SansSerif", Font.BOLD, Math.max(10, Math.round(cell * 0.46f)));
            smallFont = new Font("SansSerif", Font.PLAIN, Math.max(7, Math.round(cell * 0.26f)));
            // 每次都对齐首选尺寸：早先只在 cell 变化时才设，而首次 cell 正好等于初值，
            // 于是尺寸从未被设置过，面板被布局成 0 宽，画出来是一片空白。
            Dimension want = new Dimension(C*cell + 2*pad, R*cell + 2*pad);
            if (!want.equals(getPreferredSize())) { setPreferredSize(want); revalidate(); }
        }
        void chord(int i) {
            if (board == null || board.dead || !board.rev[i]) return;
            int v = board.num(i);
            if (v == 0) return;
            int flags = 0;
            for (int p : board.nbs(i)) if (board.flag[p]) flags++;
            if (flags != v) return;
            pushHistory();                    // 连开周围也是一步，撤销要能退回连开之前
            for (int p : board.nbs(i)) {
                if (board.dead) break;                          // 已经踩雷就别再顺着循环往下开
                if (!board.rev[p] && !board.flag[p]) { board.open(p); topUpWeak(); }
            }
            if (board.revCount() == R*C - M)
                for (int k = 0; k < R*C; k++) if (board.mine[k]) board.flag[k] = true;
            repaint(); updateCounters();
        }
        void hit(MouseEvent e) {
            if (board == null) return;
            int c = (e.getX() - ox) / cell, r = (e.getY() - oy) / cell;
            if (!board.inside(r, c)) return;
            int i = board.I(r, c);
            if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
                if (!board.rev[i]) { pushHistory(); board.flag[i] = !board.flag[i]; repaint(); updateCounters(); }
                return;
            }
            if (board.rev[i]) { describeCell(i); return; }
            if (board.flag[i] || board.dead) return;
            pushHistory();
            board.open(i);
            topUpWeak();
            if (board.revCount() == R*C - M) for (int k = 0; k < R*C; k++) if (board.mine[k]) board.flag[k] = true;
            repaint(); updateCounters();
        }
        Font numFont = new Font("SansSerif", Font.BOLD, 17);
        Font smallFont = new Font("SansSerif", Font.PLAIN, 10);
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            for (int r = 0; r < R; r++) for (int c = 0; c < C; c++) {
                int i = board.I(r, c);
                int x = ox + c*cell, y = oy + r*cell;
                if (!board.rev[i]) {
                    g.setColor(new Color(0xC9D6E2));
                    g.fillRoundRect(x+1, y+1, cell-2, cell-2, 5, 5);
                    g.setColor(new Color(0xEFF5FA));
                    g.drawRoundRect(x+2, y+2, cell-4, cell-4, 5, 5);
                    if (board.flag[i]) {
                        g.setColor(new Color(0xD64045));
                        int[] xs = {x+cell/2-6, x+cell/2+7, x+cell/2-6};
                        int[] ys = {y+7, y+cell/2-1, y+cell/2-9};
                        g.fillPolygon(xs, ys, 3);
                        g.setColor(new Color(0x6B7A88));
                        g.fillRect(x+cell/2-7, y+8, 2, cell-14);
                    }
                } else {
                    g.setColor(new Color(0xF7FAFC));
                    g.fillRect(x+1, y+1, cell-2, cell-2);
                    g.setColor(new Color(0xDCE6EF));
                    g.drawRect(x+1, y+1, cell-2, cell-2);
                    if (board.mine[i]) { drawMine(g, x, y); continue; }
                    int v = board.num(i);
                    if (v > 0) {
                        g.setFont(numFont);
                        g.setColor(numColor(v));
                        String s = String.valueOf(v);
                        FontMetrics fm = g.getFontMetrics();
                        int tw = fm.stringWidth(s);
                        g.drawString(s, x + cell/2 - tw/2, y + cell/2 + fm.getAscent()/2 - 2);
                    }
                }
            }
            if (hintBox.isSelected()) drawHints(g);
            if (highlight >= 0) {
                int hr = highlight / C, hc = highlight % C;
                g.setColor(new Color(0xFFB300));
                g.setStroke(new BasicStroke(2.6f));
                g.drawRect(ox + hc*cell + 1, oy + hr*cell + 1, cell - 2, cell - 2);
            }
            if (board.dead) {
                g.setColor(new Color(0x00000022));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
        void drawHints(Graphics2D g) {
            g.setFont(smallFont);
            FontMetrics fm = g.getFontMetrics();
            HashSet<Integer> drawn = new HashSet<>();
            for (Hint h : weak) {
                if (!board.rev[h.i] || drawn.contains(h.i)) continue;
                boolean any = false;
                for (int p : board.shapeCells(h.i, h.key)) if (!board.rev[p]) any = true;
                // 形状里没有未开格可标，画出来没有对象
                if (!any) continue;
                if (densityLevel == 0 && !h.exact) continue;
                if (densityLevel == 1 && !h.exact && h.lb < 2) continue;
                int r = h.i / C, c = h.i % C;
                int x = ox + c*cell, y = oy + r*cell;
                // 只有两种颜色，位置就是语义：
                //   右下角「共有 n 颗」→ 蓝；右上角「至少 n 颗」→ 橙。
                // 下界为 0 的提示没有数字可写，只画一个淡色符号。
                if (h.exact) {
                    String txt = DISP.get(h.key) + h.lb;
                    int tw = fm.stringWidth(txt);
                    g.setColor(new Color(0x2F, 0x6F, 0xA8));
                    g.drawString(txt, x + cell - tw - 3, y + cell - 4);
                } else if (h.lb >= 1) {
                    String txt = DISP.get(h.key) + h.lb;
                    int tw = fm.stringWidth(txt);
                    g.setColor(new Color(0xB0, 0x6A, 0x00));
                    g.drawString(txt, x + cell - tw - 3, y + 11);
                } else {
                    String txt = DISP.get(h.key);
                    int tw = fm.stringWidth(txt);
                    g.setColor(new Color(0xAE, 0xBF, 0xCE));
                    g.drawString(txt, x + cell - tw - 3, y + cell - 4);
                }
                drawn.add(h.i);
            }
            for (Hint h : hints) {
                if (!board.rev[h.i] || drawn.contains(h.i)) continue;
                int r = h.i / C, c = h.i % C;
                int x = ox + c*cell, y = oy + r*cell;
                if (!h.exact && h.lb == 0) {
                    // 与 weak 同一口径：下界为 0 只画淡色符号
                    String txt = DISP.get(h.key);
                    int tw = fm.stringWidth(txt);
                    g.setColor(new Color(0xAE, 0xBF, 0xCE));
                    g.drawString(txt, x + cell - tw - 3, y + cell - 4);
                } else {
                    String txt = DISP.get(h.key) + h.lb;
                    int tw = fm.stringWidth(txt);
                    g.setColor(new Color(0x2F, 0x6F, 0xA8));
                    g.drawString(txt, x + cell - tw - 3, y + cell - 4);
                }
                drawn.add(h.i);
            }
        }

        void drawMine(Graphics2D g, int x, int y) {
            g.setColor(new Color(0x2B3A45));
            int cx = x + cell/2, cy = y + cell/2, rr = Math.max(4, cell/5);
            g.fillOval(cx-rr, cy-rr, 2*rr, 2*rr);
            g.setStroke(new BasicStroke(1.4f));
            g.drawLine(cx-rr-3, cy, cx+rr+3, cy);
            g.drawLine(cx, cy-rr-3, cx, cy+rr+3);
        }
        Color numColor(int v) {
            switch (v) {
                case 1: return new Color(0x2F6FA8);
                case 2: return new Color(0x2E7D46);
                case 3: return new Color(0xC0392B);
                case 4: return new Color(0x5B3F94);
                default: return new Color(0x8B5A2B);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        final String shot = args.length >= 2 && args[0].equals("shot") ? args[1] : null;
        // 第三个参数可选：提示档位 0=精简 1=标准 2=全部。截图核对不同档位时用。
        final int density = args.length >= 3 ? Integer.parseInt(args[2]) : -1;
        // 第四个参数可选：难度 0=初级 1=中级 2=高级。
        final int level = args.length >= 4 ? Integer.parseInt(args[3]) : -1;
        SwingUtilities.invokeLater(() -> {
            Sweeper f = new Sweeper();
            if (level >= 0 && level < LEVELS.length) f.levelBox.setSelectedIndex(level);
            if (density >= 0 && density <= 2) f.densityBox.setSelectedIndex(density);
            f.setVisible(true);
            if (shot != null) {
                javax.swing.Timer t = new javax.swing.Timer(900, ev -> {
                    try {
                        // 按内容面板的实际尺寸取图：窗口尺寸含标题栏与边框，
                        // 而 paint 画的是内容面板，两者一差就在底部留一条黑边。
                        java.awt.Container pane = f.getContentPane();
                        int w = pane.getWidth(), h = pane.getHeight();
                        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = img.createGraphics();
                        pane.paint(g);
                        g.dispose();
                        ImageIO.write(img, "png", new File(shot));
                        System.out.println("saved " + shot + " " + w + "x" + h);
                    } catch (Exception ex) { ex.printStackTrace(); }
                    System.exit(0);
                });
                t.setRepeats(false);
                t.start();
            }
        });
    }
}
