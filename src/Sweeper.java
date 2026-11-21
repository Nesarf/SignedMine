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
        SHAPES.put("pm",new int[]{1,2,3,6,7,8});  DISP.put("pm","±");
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
        Hint hintFor(int q, int[] used) {
            if (!rev[q]) return null;
            int v = num(q);
            if (v == 0) return null;
            int base = rest(q);
            int[] nb = NB[q];
            Hint best = null;
            int bestScore = Integer.MIN_VALUE;
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
                if (lb > un) lb = un;                      // 形状内雷数不可能超过未开格数
                boolean exact = (lb == base);
                if (!exact && lb < 1) continue;             // 「至少 0 颗」不携带信息，不显示
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
                Hint h = hintFor(q, used);
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
                    if (lb >= un || base <= 0) {
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
    static boolean[] snapshot;
    static int R = 16, C = 30, M = 100;
    static String mode = "高级 16×30 · 100 雷";

    // 只取 n 最大、且还没用过的那一条；同 n 时保留先出现的（与「按 n 稳定降序取首个」等价）。
    static Hint pickCandidate(ArrayList<Hint> cands, HashSet<Integer> used) {
        Hint best = null; int bestN = -1;
        for (Hint c : cands) {
            if (c.n <= bestN) continue;
            if (used.contains(c.i * 64 + keyIndex(c.key))) continue;
            bestN = c.n; best = c;
        }
        return best;
    }

    // 整表重算（新局 / 主动刷新时用）
    static void refreshAllWeak() {
        weakByCell = new Hint[R * C];
        weak = new ArrayList<>();
        int[] used = new int[NK];
        for (int q = 0; q < R * C; q++) {
            Hint h = board.hintFor(q, used);
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
        Hint h = board.hintFor(q, used);
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
            if (stall >= 3) break;
            Hint pick = pickCandidate(b.candidates(), usedKey);
            if (pick == null) break;
            hs.add(pick);
            usedKey.add(pick.i * 64 + keyIndex(pick.key));
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
        String[] levels = {"初级 9×9 · 10 雷", "中级 16×16 · 40 雷", "高级 16×30 · 100 雷"};
        JComboBox<String> lv = new JComboBox<>(levels);
        lv.setSelectedIndex(2);
        lv.addActionListener(e -> {
            int idx = lv.getSelectedIndex();
            if (idx == 0) start(9, 9, 10);
            else if (idx == 1) start(16, 16, 40);
            else start(16, 30, 100);
        });
        bar.add(new JLabel(" 难度 "));
        bar.add(lv);
        bar.addSeparator();
        bar.add(ng);
        bar.addSeparator();
        bar.add(hintBox);
        hintBox.addActionListener(e -> panel.repaint());
        String[] dens = {"精简", "标准", "全部"};
        JComboBox<String> density = new JComboBox<>(dens);
        density.setSelectedIndex(1);
        density.addActionListener(e -> { densityLevel = density.getSelectedIndex(); panel.repaint(); });
        bar.add(new JLabel(" 提示 "));
        bar.add(density);
        JButton hb = new JButton("提示");
        hb.addActionListener(e -> showHint());
        bar.add(hb);
        bar.add(Box.createHorizontalGlue());
        bar.add(mineLabel);
        bar.addSeparator();
        bar.add(timeLabel);
        bar.add(new JLabel("  "));
        add(bar, BorderLayout.NORTH);
        add(panel, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        clock = new javax.swing.Timer(100, e -> {
            if (running) {
                long ms = System.currentTimeMillis() - startedAt;
                timeLabel.setText(String.format("%.1fs", ms / 1000.0));
            }
        });
        clock.start();

        start(16, 30, 100);
        setSize(1180, 780);
        setLocationRelativeTo(null);
    }

    void start(int rows, int cols, int mines) {
        R = rows; C = cols; M = mines;
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

    String bestKey(){ return "best_" + R + "x" + C + "_" + M; }
    double bestSeconds(){
        try { return Preferences.userNodeForPackage(Sweeper.class).getDouble(bestKey(), 0); }
        catch (Exception e) { return 0; }
    }
    void saveBest(double v){
        try { Preferences.userNodeForPackage(Sweeper.class).putDouble(bestKey(), v); }
        catch (Exception e) { }
    }

    void describeCell(int i) {
        int v = board.num(i);
        if (v == 0) { status.setText(" 空格，周围没有雷"); return; }
        String exactTxt = null, weakTxt = null;
        for (Hint h : hints) if (h.i == i) exactTxt = DISP.get(h.key) + h.lb;
        for (Hint h : weak) if (h.i == i) weakTxt = DISP.get(h.key) + h.lb;
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
        int ex = 0; for (Hint h : weak) if (h.exact) ex++;
        mineLabel.setText("剩余雷 " + (M - flags) + " · 精确 " + ex + " / 至少 " + (weak.size() - ex) + "  ");
        if (board.dead) { status.setText(" 踩到雷了 —— 按新局再来"); running = false; }
        else if (board.revCount() == R*C - M) {
            double sec = (System.currentTimeMillis() - startedAt) / 1000.0;
            double prev = bestSeconds();
            double best = (prev <= 0 || sec < prev) ? sec : prev;
            if (prev <= 0 || sec < prev) saveBest(sec);
            status.setText(String.format(" 清完了 —— %.1f 秒（最佳 %.1f 秒，%d 条提示）", sec, best, hints.size()));
            running = false;
        }
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
            int availW = getWidth() > 0 ? getWidth() : 1180;
            int availH = getHeight() > 0 ? getHeight() : 740;
            int cw = (availW - 2*pad) / Math.max(C, 1);
            int ch = (availH - 2*pad) / Math.max(R, 1);
            int next = Math.max(18, Math.min(40, Math.min(cw, ch)));
            boolean changed = (next != cell);
            cell = next;
            centre();
            if (changed) {
                Dimension want = new Dimension(C*cell + 2*pad, R*cell + 2*pad);
                if (!want.equals(getPreferredSize())) { setPreferredSize(want); revalidate(); }
            }
        }
        // 棋盘不去顶格：按面板实际尺寸居中，窗口放大时空白留在四周而不是全堆在右下。
        void centre() {
            ox = Math.max(pad, (getWidth() - C*cell) / 2);
            oy = Math.max(pad, (getHeight() - R*cell) / 2);
        }
        void chord(int i) {
            if (board == null || board.dead || !board.rev[i]) return;
            int v = board.num(i);
            if (v == 0) return;
            int flags = 0;
            for (int p : board.nbs(i)) if (board.flag[p]) flags++;
            if (flags != v) return;
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
                if (!board.rev[i]) { board.flag[i] = !board.flag[i]; repaint(); updateCounters(); }
                return;
            }
            if (board.rev[i]) { describeCell(i); return; }
            if (board.flag[i] || board.dead) return;
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
                if (!any) continue;
                if (densityLevel == 0 && !h.exact) continue;
                if (densityLevel == 1 && !h.exact && h.lb < 2) continue;
                int r = h.i / C, c = h.i % C;
                int x = ox + c*cell, y = oy + r*cell;
                String txt = DISP.get(h.key) + h.lb;
                int tw = fm.stringWidth(txt);
                if (h.exact) {
                    g.setColor(new Color(0x2F, 0x6F, 0xA8));
                    g.drawString(txt, x + cell - tw - 3, y + cell - 4);
                } else {
                    g.setColor(new Color(0xB0, 0x6A, 0x00));
                    g.drawString(txt, x + cell - tw - 3, y + 11);
                }
                drawn.add(h.i);
            }
            for (Hint h : hints) {
                if (!board.rev[h.i] || drawn.contains(h.i)) continue;
                drawn.add(h.i);
                int r = h.i / C, c = h.i % C;
                int x = ox + c*cell, y = oy + r*cell;
                String txt = DISP.get(h.key) + h.lb;
                int tw = fm.stringWidth(txt);
                g.setColor(new Color(0x2F, 0x6F, 0xA8));
                g.drawString(txt, x + cell - tw - 3, y + cell - 4);
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
        SwingUtilities.invokeLater(() -> {
            Sweeper f = new Sweeper();
            f.setVisible(true);
            if (shot != null) {
                javax.swing.Timer t = new javax.swing.Timer(900, ev -> {
                    try {
                        BufferedImage img = new BufferedImage(f.getWidth(), f.getHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = img.createGraphics();
                        f.getContentPane().paint(g);
                        g.dispose();
                        ImageIO.write(img, "png", new File(shot));
                        System.out.println("saved " + shot + " " + img.getWidth() + "x" + img.getHeight());
                    } catch (Exception ex) { ex.printStackTrace(); }
                    System.exit(0);
                });
                t.setRepeats(false);
                t.start();
            }
        });
    }
}
