// 扫雷引擎（JS）：发牌、形状提示、求援与求解。
//
// android/assets/sweeper.html 里内联着同一套引擎（单文件是该应用的设计约束），
// 两份必须同步：改这里就要改那里，test/check-parity.mjs 会比两边的引擎段，漂移即报错。
//
// 优化记录（与旧版行为逐局一致，由 test/golden.json 定格）：
//   - 邻域表在构造时算好，heat path 的 nbs() 不再每次分配数组
//   - 形状格按 (格, 形状) 惰性缓存，25 个形状的重复展开只算一次
//   - 数字缓存从 Map 换成 Int8Array，避开每格一次哈希查找
//   - 成员判定用线性扫描代替对 4~6 格的小数组建 Set
//   - candidates() 不再排整表，build() 就地取最优（与稳定排序后取首个等价）

const OFFS = {1:[-1,-1],2:[-1,0],3:[-1,1],4:[0,-1],5:[0,1],6:[1,-1],7:[1,0],8:[1,1]};
// 形状按八邻域编号书写，左上为 1、右下为 8。
// ± 与 ∓ 的格子不是「上下两整行」，而是按符号本身的笔画取的：
//   ± = 加号（2-4-5-7）在上、减号（6-7-8）在下，并起来 245678；
//   ∓ = 它的上下镜像：减号（1-2-3）在上、加号（2-4-5-7）在下，即 123457。
export const SHAPES = {
  up:[1,2,3], down:[6,7,8], left:[1,4,6], right:[3,5,8],
  ul:[1,2,4], ur:[2,3,5], dl:[4,6,7], dr:[5,7,8],
  cross:[1,3,6,8], sq:[3,4,7], mu:[1,3,4,5,6], integ:[2,3,6,7],
  vert:[2,7], dash:[4,5], bs:[1,8], fs:[3,6],
  neq:[2,4,5,7], pm:[2,4,5,6,7,8], mp:[1,2,3,4,5,7], corner:[1,2,3,4],
  tf:[2,6,8], bf:[1,3,7], ang:[3,6,8], lt:[2,4,7], gt:[2,5,7]
};
export const DISP = {up:"↑",down:"↓",left:"←",right:"→",ul:"↖",ur:"↗",dl:"↙",dr:"↘",
  cross:"×",sq:"√",mu:"μ",integ:"∫",vert:"∣",dash:"—",bs:"╲",fs:"╱",
  neq:"≠",pm:"±",mp:"∓",corner:"⌐",tf:"∴",bf:"∵",ang:"∠",lt:"〈",gt:"〉"};

// 形状键的稳定顺序 = 字面量的书写顺序（字符串键，无整数键，与 for...in 同序）。
const KEYS = Object.keys(SHAPES);
const NK = KEYS.length;
const KEY_INDEX = new Map(KEYS.map((k, i) => [k, i]));
// 每个形状的八邻域编号先展开成 [dr,dc]，shapeCells 里就不必再查 OFFS
const SHAPE_OFFS = KEYS.map((k) => SHAPES[k].map((id) => OFFS[id]));

function inCells(cells, p){
  for (let i = 0; i < cells.length; i++) if (cells[i] === p) return true;
  return false;
}

function makeRng(seed){
  let a = seed >>> 0;
  return function(){
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export class Board {
  constructor(R,C,m,seed){
    this.R=R; this.C=C; this.m=m; this.seed=seed;
    this.rng=makeRng(seed);
    this.mines=new Set(); this.rev=new Set(); this.known=new Set();
    this.flag=new Set(); this.dead=false; this.placed=false;

    const N=R*C;
    // 数字缓存：-1 表示未算。-1 以外的值就是邻雷数。
    this._num=new Int8Array(N).fill(-1);
    // 邻域表：一次算好，之后只读（旧版每次 nbs() 都新建一个数组）
    const nb=new Array(N);
    for(let r=0;r<R;r++)for(let c=0;c<C;c++){
      const out=[];
      for(let dr=-1;dr<=1;dr++)for(let dc=-1;dc<=1;dc++){
        if(!dr&&!dc) continue;
        const rr=r+dr, cc=c+dc;
        if(rr>=0&&rr<R&&cc>=0&&cc<C) out.push(rr*C+cc);
      }
      nb[r*C+c]=out;
    }
    this._nb=nb;
    // 形状格缓存：(格号 * NK + 形状序号) → 格号数组。惰性填充，只依赖 R/C。
    this._sc=new Array(N*NK).fill(null);
  }
  inside(r,c){ return r>=0&&r<this.R&&c>=0&&c<this.C; }
  I(r,c){ return r*this.C+c; }
  RC(i){ return [Math.floor(i/this.C), i%this.C]; }
  nbs(i){ return this._nb[i]; }
  num(i){
    const got=this._num[i];
    if(got>=0) return got;
    const nb=this._nb[i];
    let n=0;
    for(let t=0;t<nb.length;t++) if(this.mines.has(nb[t])) n++;
    this._num[i]=n; return n;
  }
  place(safe){
    const [sr,sc]=this.RC(safe), pool=[];
    for(let r=0;r<this.R;r++)for(let c=0;c<this.C;c++)
      if(Math.max(Math.abs(r-sr),Math.abs(c-sc))>1) pool.push(this.I(r,c));
    for(let i=pool.length-1;i>0;i--){ const j=Math.floor(this.rng()*(i+1)); [pool[i],pool[j]]=[pool[j],pool[i]]; }
    this.mines=new Set(pool.slice(0,this.m)); this.placed=true; this._num.fill(-1);
  }
  open(i){
    if(this.rev.has(i)) return;
    if(!this.placed) this.place(i);
    if(this.mines.has(i)){ this.dead=true; this.rev.add(i); return; }
    const st=[i];
    while(st.length){
      const p=st.pop();
      if(this.rev.has(p)||this.mines.has(p)) continue;
      this.rev.add(p);
      if(this.num(p)===0) for(const q of this.nbs(p)) if(!this.rev.has(q)) st.push(q);
    }
  }
  unk(i){ return !this.rev.has(i)&&!this.known.has(i); }
  rest(i){ let n=this.num(i); for(const j of this.nbs(i)) if(this.known.has(j)) n--; return n; }
  // 局面位图：0 = 已翻开，1 = 未开未标（可推断对象），2 = 已标 / 已推定是雷。
  // 热路径上要判定上千万次「这格是什么状态」，逐次走 Set.has 是这里最贵的一笔；
  // 一次 O(R*C) 扫描把状态摊平，之后全是数组访问。
  stateMap(){
    const N=this.R*this.C, s=new Uint8Array(N);
    for(let i=0;i<N;i++) s[i]= this.rev.has(i) ? 0 : (this.known.has(i) ? 2 : 1);
    return s;
  }
  localRules(){
    const mi=new Set(), sa=new Set();
    // 按格号升序遍历：src/Sweeper.java 就是这么走的。
    // 集合内容不受顺序影响，但 candidates() 的输出顺序会，而 pickCandidate 在 n
    // 相同时取先出现的那条 —— 顺序一致，两侧的选择才必然一致，而不是碰巧一致。
    const N=this.R*this.C;
    for(let q=0;q<N;q++){
      if(!this.rev.has(q)) continue;
      const v=this.num(q); if(!v) continue;
      const hid=[]; for(const p of this.nbs(q)) if(this.unk(p)) hid.push(p);
      if(!hid.length) continue;
      const r=this.rest(q);
      if(r===0) hid.forEach(p=>sa.add(p));
      else if(r===hid.length) hid.forEach(p=>mi.add(p));
    }
    return [mi,sa];
  }
  subsetRules(){
    const mi=new Set(), sa=new Set(), info=new Map();
    const N=this.R*this.C;
    for(let q=0;q<N;q++){
      if(!this.rev.has(q)) continue;
      if(!this.num(q)) continue;
      const h=new Set(); for(const p of this.nbs(q)) if(this.unk(p)) h.add(p);
      if(h.size) info.set(q,{h,r:this.rest(q)});
    }
    const isSub=(a,b)=>{ for(const x of a) if(!b.has(x)) return false; return true; };
    for(const [a,ia] of info){
      const [ar,ac]=this.RC(a);
      for(let dr=-2;dr<=2;dr++)for(let dc=-2;dc<=2;dc++){
        if(!dr&&!dc) continue;
        if(!this.inside(ar+dr,ac+dc)) continue;
        const ib=info.get(this.I(ar+dr,ac+dc)); if(!ib) continue;
        let small,big,d;
        if(ia.h.size<ib.h.size&&isSub(ia.h,ib.h)){ small=ia.h; big=ib.h; d=ib.r-ia.r; }
        else if(ib.h.size<ia.h.size&&isSub(ib.h,ia.h)){ small=ib.h; big=ia.h; d=ia.r-ib.r; }
        else continue;
        const diff=[]; for(const x of big) if(!small.has(x)) diff.push(x);
        if(!diff.length) continue;
        if(d===diff.length) diff.forEach(x=>mi.add(x));
        else if(d===0) diff.forEach(x=>sa.add(x));
      }
    }
    return [mi,sa];
  }
  // 提示约束参与联合推理。
  // 提示给出的是「形状内雷数 ∈ [lo, hi]」，局部约束是「邻域内雷数 = rest」。
  // 两者做子集推理就能推出差集：若 A ⊂ B，则 B\A 的雷数 ∈ [lo_B - hi_A, hi_B - lo_A]，
  // 上界 ≤ 0 是差集全安全，下界够到差集大小是差集全雷。
  // 旧版只拿局部约束互相比较，提示给的区间完全没参与，所以大盘只能塞进 18% 的密度。
  hintSubsetRules(hints){
    const mi=new Set(), sa=new Set();
    if(!hints||!hints.length) return [mi,sa];
    const N=this.R*this.C, loc=new Array(N);
    for(let q=0;q<N;q++){
      if(!this.rev.has(q)||!this.num(q)) continue;
      const cells=[]; for(const p of this.nbs(q)) if(this.unk(p)) cells.push(p);
      if(!cells.length) continue;
      const r=this.rest(q);
      loc[q]={cells,lo:r,hi:r};
    }
    const hc=[];
    for(const h of hints){
      const all=this.shapeCells(h.i,h.key), cells=[];
      for(const p of all) if(this.unk(p)) cells.push(p);
      if(!cells.length) continue;
      const base=this.rest(h.i);
      let outside=0;
      for(const p of this.nbs(h.i)) if(this.unk(p)&&!inCells(all,p)) outside++;
      hc.push({centre:h.i,cells,lo:Math.max(0,base-outside),hi:base});
    }
    for(const A of hc){
      const [ar,ac]=this.RC(A.centre);
      for(let dr=-2;dr<=2;dr++)for(let dc=-2;dc<=2;dc++){
        if(!dr&&!dc) continue;
        if(!this.inside(ar+dr,ac+dc)) continue;
        const B=loc[this.I(ar+dr,ac+dc)];
        if(!B) continue;
        this.subsetPush(A,B,mi,sa);
        this.subsetPush(B,A,mi,sa);
      }
    }
    return [mi,sa];
  }
  // small 的格集合若是 big 的子集，就把差集推出来
  subsetPush(small,big,mi,sa){
    if(small.cells.length>=big.cells.length) return;
    for(const x of small.cells) if(!inCells(big.cells,x)) return;
    const diff=[]; for(const x of big.cells) if(!inCells(small.cells,x)) diff.push(x);
    if(!diff.length) return;
    const lo=Math.max(0,big.lo-small.hi), hi=big.hi-small.lo;
    if(hi<=0) diff.forEach(x=>sa.add(x));
    else if(lo>=diff.length) diff.forEach(x=>mi.add(x));
  }
  // 某格在某形状下覆盖到的格号（越界的边格被裁掉），按 (格,形状) 缓存
  shapeCellsK(q,k){
    const at=q*NK+k, hit=this._sc[at];
    if(hit!==null) return hit;
    const offs=SHAPE_OFFS[k], r=(q/this.C)|0, c=q%this.C, out=[];
    for(let t=0;t<offs.length;t++){
      const rr=r+offs[t][0], cc=c+offs[t][1];
      if(rr>=0&&rr<this.R&&cc>=0&&cc<this.C) out.push(rr*this.C+cc);
    }
    this._sc[at]=out; return out;
  }
  shapeCells(q,key){ return this.shapeCellsK(q,KEY_INDEX.get(key)); }
  hintEffect(hints){
    const mi=new Set(), sa=new Set();
    for(const h of hints){
      const all=this.shapeCells(h.i,h.key), cells=[];
      for(let t=0;t<all.length;t++) if(this.unk(all[t])) cells.push(all[t]);
      if(!cells.length) continue;
      const base=this.rest(h.i);
      // 形状内雷数恰好落在 [base-outside, base]：
      //   上界是 base —— 形状内的雷都是该数字数到的雷，不会超过它的剩余雷数；
      //   下界是 base-outside —— 形状外每个未开邻格最多吃掉 1 颗。
      // 于是 rest<=0 时形状内必然 0 颗（全安全），这是严格结论，不需要猜；
      // 而 rest>0 且 outside>=rest 只是「下界是 0」，等于没有信息 —— 旧版把它也当成全安全，
      // 每轮朝雷上开、踩到就判失败，高级档发牌因此反复空转（见 test/diag-candidates.mjs）。
      if(base<=0){ cells.forEach(x=>sa.add(x)); continue; }
      const nb=this.nbs(h.i);
      let outside=0;
      for(let t=0;t<nb.length;t++){ const p=nb[t]; if(this.unk(p)&&!inCells(all,p)) outside++; }
      if(base-outside>=cells.length) cells.forEach(x=>mi.add(x));
    }
    return [mi,sa];
  }
  solve(hints){
    const total=this.R*this.C-this.m; let guard=0;
    while(this.rev.size<total){
      if(guard++>20000) return false;
      let [mi,sa]=this.localRules();
      if(!mi.size&&!sa.size) [mi,sa]=this.subsetRules();
      if(!mi.size&&!sa.size) [mi,sa]=this.hintSubsetRules(hints);
      if(!mi.size&&!sa.size) [mi,sa]=this.hintEffect(hints);
      if(!mi.size&&!sa.size&&this.known.size>=this.m){
        sa=new Set(); for(let i=0;i<this.R*this.C;i++) if(this.unk(i)) sa.add(i);
      }
      const mi2=[...mi].filter(x=>!this.rev.has(x)&&!this.known.has(x));
      const sa2=[...sa].filter(x=>!this.rev.has(x)&&!this.known.has(x));
      if(!mi2.length&&!sa2.length) return false;
      mi2.forEach(x=>this.known.add(x));
      for(const x of sa2){ if(this.mines.has(x)) return false; this.open(x); }
    }
    for(const x of this.mines) this.known.add(x);
    return !this.dead;
  }
  // 候选提示：返回产生顺序（按格、按形状），不排序。
  // 旧版在这里对整表按 n 降序做过一次 sort，而调用方只取第一个未用过的，
  // 所以把「取最优」搬到 build() 里就地做，省掉每次迭代的一次大排序。
  // 候选提示，只收能推进局面的两种：
  //   下界够到未开格数 → 形状内全是雷
  //   剩余雷数为 0 → 形状内全安全
  // 与 src/Sweeper.java 的 candidates() 同一口径（同一 seed 要选出同一条提示）。
  candidates(){
    const out=[];
    const N=this.R*this.C, s=this.stateMap();
    const restc=new Int8Array(N).fill(-1);
    const restOf=(i)=>{
      let r=restc[i];
      if(r<0){
        r=this.num(i);
        const nb=this.nbs(i);
        for(let t=0;t<nb.length;t++) if(s[nb[t]]===2) r--;
        restc[i]=r;
      }
      return r;
    };
    for(let q=0;q<N;q++){
      if(!this.rev.has(q)) continue;
      const v=this.num(q); if(!v) continue;
      const base=restOf(q), nb=this.nbs(q);
      for(let k=0;k<NK;k++){
        const cells=this.shapeCellsK(q,k), cl=cells.length;
        let unk=0;
        for(let t=0;t<cl;t++) if(s[cells[t]]===1) unk++;
        if(!unk) continue;
        let outside=0;
        for(let t=0;t<nb.length;t++){ const p=nb[t]; if(s[p]===1&&!inCells(cells,p)) outside++; }
        let lb=Math.max(0, base-outside);
        // 每个已翻开邻居都能抬高下界：它的雷可能落在形状内、形状外、或两边之外
        for(let t=0;t<nb.length;t++){
          const q2=nb[t];
          if(s[q2]!==0||!this.num(q2)) continue;
          const nb2=this.nbs(q2);
          let inner=0, unkQ2=0;
          for(let u=0;u<nb2.length;u++){
            const p=nb2[u];
            if(s[p]!==1) continue;
            unkQ2++;
            if(inCells(cells,p)) inner++;
          }
          const lower=restOf(q2)-(unkQ2-inner);
          if(lower>lb) lb=lower;
        }
        if(lb>unk) lb=unk;
        // 只要约束还有内容就收：下界非零（形状内至少有雷），或上界没满（base < 未开格数）。
        // 旧条件只收「lb 够到格数」或「base 为 0」，那两种是能直接推出结论的，
        // 但开局时几乎不存在，候选池空掉，build 在第一条提示之前就放弃了。
        if(lb>=1 || base<unk) out.push({i:q,key:KEYS[k],lb:Math.max(0,lb),n:unk,v});
      }
    }
    return out;
  }
}

export function build(R,C,m,seed){
  const b=new Board(R,C,m,seed);
  b.open(b.I(Math.floor(R/2),Math.floor(C/2)));
  const snapshot=new Set(b.rev);
  const hints=[];
  const used=new Set();
  let stall=0;
  for(let t=0;t<80;t++){
    const before=b.rev.size+b.known.size;
    if(b.solve(hints)) return {board:b,hints,snapshot,ok:true};
    // 加了提示还是没有任何推进，这局基本走不通了。
    // 旧版会一路加到 80 轮才放弃，高级档的失败局因此白跑几十轮候选枚举。
    stall = (b.rev.size+b.known.size===before) ? stall+1 : 0;
    if(stall>=6) return {board:b,hints,snapshot,ok:false};
    // 每轮加一批而不是一条：大盘开局单条提示推不动局面，
    // 一条一条地加，80 轮也凑不出足够强的约束。
    const cands=b.candidates().sort((x,y)=>y.n-x.n);
    let added=0;
    for(const c of cands){
      if(added>=8) break;
      const key=c.i*64+KEY_INDEX.get(c.key);
      if(used.has(key)) continue;
      hints.push(c); used.add(key); added++;
    }
    if(!added) return {board:b,hints,snapshot,ok:false};
  }
  return {board:b,hints,snapshot,ok:false};
}

export function deal(R,C,m){
  // 单次 build 的成功率：高级档约 2%，中级过半。失败局现在很早就能判定（stall 早退），
  // 单次只要 2 ms 上下，所以把重试次数提上去很划算：把「发不出局」换成「极少数局面多等半秒」。
  for(let t=0;t<240;t++){
    const r=build(R,C,m,(Math.random()*1e9)|0);
    if(r.ok){ r.board.rev=new Set(r.snapshot); r.board.known=new Set(); r.board.dead=false; return r; }
  }
  return null;
}

// 单格的形状提示：形状内雷数落在 [下界, base]。
// 下界取两条推导的较大者（与 src/Sweeper.java 的 hintFor 同一口径）：
//   1) base - 形状外未开邻格数：形状外每个未开格最多吃掉一颗雷；
//   2) 对每个已翻开邻居 q2：rest(q2) - (q2 的未开邻格里不在形状内的个数)。
//      旧口径只看到「在形状内」的那部分，于是把「两边都不在」的格子算进了形状外，
//      上界被压到与下界相等，判出假的「精确值」。
// 上界就是 base。只有 lb === base 才写「共有 n 颗」。
// force=true 时不丢掉「至少 0 颗」这类没有结论的形状：全部显示档要给每个数字格
// 都挂上符号与小数字，就得连无信息的形状一起留着，由界面按档位自己过滤。
export function shapeHintFor(b,q,s,force){
  const v=b.num(q); if(!v) return null;
  const nb=b.nbs(q);
  let base=v;
  for(let t=0;t<nb.length;t++){ const p=nb[t]; if(s ? s[p]===2 : b.known.has(p)) base--; }
  let best=null;
  for(let k=0;k<NK;k++){
    const cells=b.shapeCellsK(q,k), cl=cells.length;
    let unk=0;
    if(s){ for(let t=0;t<cl;t++) if(s[cells[t]]===1) unk++; }
    else { for(let t=0;t<cl;t++) if(b.unk(cells[t])) unk++; }
    if(!unk) continue;
    let outside=0;
    for(let t=0;t<nb.length;t++){
      const p=nb[t];
      if(!(s ? s[p]===1 : b.unk(p))) continue;
      if(!inCells(cells,p)) outside++;
    }
    let lb=Math.max(0, base-outside);
    for(let t=0;t<nb.length;t++){
      const q2=nb[t];
      if(!(s ? s[q2]===0 : b.rev.has(q2))) continue;
      const r2=b.rest(q2); if(!r2) continue;
      const nb2=b.nbs(q2);
      let inner=0, unkQ2=0;
      for(let u=0;u<nb2.length;u++){
        const p=nb2[u];
        if(!(s ? s[p]===1 : b.unk(p))) continue;
        unkQ2++;
        if(inCells(cells,p)) inner++;
      }
      const lower=r2-(unkQ2-inner);
      if(lower>lb) lb=lower;
    }
    if(lb>unk) lb=unk;
    const exact=(lb===base);
    if(!exact && lb<1 && !force) continue;
    const score=(exact?200:0)+lb*100-unk;
    if(!best||score>best.score) best={score,key:KEYS[k],lb,exact,n:unk};
  }
  if(!best) return null;
  return {i:q,key:best.key,lb:best.lb,exact:best.exact,n:best.n,
    trivial:best.exact&&(best.lb===0||best.lb>=best.n)};
}

// force=true 时每个已翻开的数字格都会得到一条（可能 lb=0 的）提示。
export function shapeHints(b, force){
  // 只输出能严格证明的提示：
  //   下界够到未开格数 → 形状内全是雷
  //   剩余雷数为 0 → 形状内全安全
  //   下界等于剩余雷数 → 「共有 n 颗」
  // 原版把「下界为 0」当成「全安全」，等于在求解器里朝雷上开。
  // 按格号升序遍历，与 src/Sweeper.java 的 weakHints() 一致。
  const out=[], s=b.stateMap(), N=b.R*b.C;
  for(let q=0;q<N;q++){
    if(!b.rev.has(q)) continue;
    const h=shapeHintFor(b,q,s,force);
    if(h) out.push(h);
  }
  return out;
}

// 增量补算：只碰 dirty 里的格子，已经算过的提示一律不动（旧版每次翻格都全盘重算）。
// dirty 的调用方契约：新翻开的格 + 它们的邻居。
export function topUpHints(b, hintMap, dirty, force){
  let added=0, s=null;
  for(const q of dirty){
    if(hintMap.has(q)||!b.rev.has(q)) continue;
    if(!s) s=b.stateMap();
    const h=shapeHintFor(b,q,s,force);
    if(h){ hintMap.set(q,h); added++; }
  }
  return added;
}
