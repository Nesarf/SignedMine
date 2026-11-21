// 诊断：失败的 build 到底在加什么提示。
// 用法：node test/diag-candidates.mjs [seed]
import { Board } from '../lib/core.js';

const SHAPES = (await import('../lib/core.js')).SHAPES;
const KEYS = Object.keys(SHAPES);
const KEY_INDEX = new Map(KEYS.map((k, i) => [k, i]));

function exactOf(b, q, key) {
  const cells = b.shapeCells(q, key);
  const set = new Set(cells);
  let outside = 0;
  for (const p of b.nbs(q)) if (b.unk(p) && !set.has(p)) outside++;
  return outside === 0;
}

function pickCandidate(cands, used) {
  let best = null, bestN = -1;
  for (const c of cands) {
    if (c.n <= bestN) continue;
    if (used.has(c.i * 64 + KEY_INDEX.get(c.key))) continue;
    bestN = c.n; best = c;
  }
  return best;
}

const seed = Number(process.argv[2] || 2);
const b = new Board(16, 30, 100, seed);
b.open(b.I(8, 15));
const hints = [];
const used = new Set();
let zeroLb = 0, exactCount = 0;

for (let t = 0; t < 80; t++) {
  const revBefore = b.rev.size, knownBefore = b.known.size;
  if (b.solve(hints)) { console.log(`seed ${seed}: 第 ${t} 轮成功，提示 ${hints.length} 条`); break; }
  const progressed = (b.rev.size !== revBefore || b.known.size !== knownBefore);
  const pick = pickCandidate(b.candidates(), used);
  if (!pick) { console.log(`seed ${seed}: 第 ${t} 轮没有新候选，放弃（提示 ${hints.length} 条）`); break; }
  const ex = exactOf(b, pick.i, pick.key);
  if (pick.lb === 0) zeroLb++;
  if (ex) exactCount++;
  if (t < 15 || !progressed) {
    console.log(`  轮 ${String(t).padStart(2)} 上轮推进=${progressed ? '是' : '否'}  pick R${(pick.i / 30 | 0) + 1}C${pick.i % 30 + 1} ${pick.key} lb=${pick.lb} n=${pick.n} exact=${ex ? '是' : '否'}`);
  }
  hints.push(pick);
  used.add(pick.i * 64 + KEY_INDEX.get(pick.key));
}
console.log(`  统计：lb=0 的提示 ${zeroLb} 条，精确提示 ${exactCount} 条 / 共 ${hints.length} 条`);
