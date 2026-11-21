// 分阶段计时：找出 16x30 发牌的时间到底花在哪一段。
// 用法：node test/profile.mjs
import { Board, build } from '../lib/core.js';

const t = { localRules: 0, subsetRules: 0, candidates: 0, hintEffect: 0, open: 0, shapeCellsK: 0 };
const n = { localRules: 0, subsetRules: 0, candidates: 0, hintEffect: 0, open: 0, shapeCellsK: 0, solve: 0, iter: 0 };

function wrap(name, direct) {
  const f = Board.prototype[name];
  Board.prototype[name] = function (...a) {
    const s = performance.now();
    const r = f.apply(this, a);
    t[name] += performance.now() - s;
    n[name]++;
    return r;
  };
}
for (const k of ['localRules', 'subsetRules', 'candidates', 'hintEffect', 'open', 'shapeCellsK']) wrap(k);

// solve 的迭代次数：用 hintEffect + localRules + subsetRules 三者的调用数近似不了，直接数 open 与 while
const origSolve = Board.prototype.solve;
Board.prototype.solve = function (hints) {
  n.solve++;
  return origSolve.call(this, hints);
};

const SEEDS = [1, 2, 3, 7, 12345, 999999, 20261015, 424242, 8888, 31337];
const t0 = performance.now();
for (const s of SEEDS) build(16, 30, 100, s);
const wall = performance.now() - t0;

console.log(`总墙钟 ${wall.toFixed(1)} ms / ${SEEDS.length} 局 → 单局 ${(wall / SEEDS.length).toFixed(1)} ms`);
const rows = Object.keys(t).map((k) => ({ name: k, ms: t[k], calls: n[k] }))
  .sort((a, b) => b.ms - a.ms);
for (const r of rows) {
  console.log(`  ${r.name.padEnd(14)} ${r.ms.toFixed(1).padStart(8)} ms  ${String(r.calls).padStart(7)} 次  占比 ${(r.ms / wall * 100).toFixed(1)}%`);
}
console.log(`  solve 调用 ${n.solve} 次`);
