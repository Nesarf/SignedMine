// 基线与回归用的性能/正确性脚本（不进包，仅本地验证用）。
// 用法：node test/bench.mjs
import { deal, build, shapeHints, Board } from '../lib/core.js';

function once(label, fn, times = 1) {
  const t0 = performance.now();
  let last;
  for (let i = 0; i < times; i++) last = fn();
  const t1 = performance.now();
  console.log(`${label}: ${(t1 - t0).toFixed(1)} ms / ${times} 次 · 单次 ${((t1 - t0) / times).toFixed(2)} ms`);
  return last;
}

// 1) 发牌：三档
for (const [R, C, M] of [[9, 9, 10], [16, 16, 40], [16, 30, 100]]) {
  let ok = 0, nul = 0;
  once(`deal ${R}x${C}/${M}`, () => {
    const r = deal(R, C, M);
    if (r) ok++; else nul++;
    return r;
  }, R * C > 300 ? 5 : 10);
  console.log(`  -> 成功 ${ok} / 失败 ${nul}`);
}

// 2) 中局重算（openAt 每次翻格都会调 shapeHints，这是卡顿的主因）
const g = deal(16, 30, 100);
if (!g) { console.log('发牌失败，跳过中局测试'); process.exit(1); }
const b = g.board;
console.log(`开局已翻开 ${b.rev.size} 格`);

// 模拟玩家翻开一半的格子
let guard = 0;
while (b.rev.size < 480 * 0.5 && guard++ < 2000) {
  for (let i = 0; i < 480; i++) if (b.unk(i) && !b.mines.has(i)) { b.open(i); break; }
}
console.log(`中局已翻开 ${b.rev.size} 格`);
once('shapeHints 中局', () => shapeHints(b), 20);

// 3) 邻域函数的使用强度：单格 num() 冷热
const b2 = new Board(16, 30, 100, 12345);
once('nbs() 48000 次', () => { let n = 0; for (let i = 0; i < 480; i++) for (let k = 0; k < 100; k++) n += b2.nbs(i).length; return n; }, 1);

// 4) 提示的「恒成立」抽查：hint 覆盖形状内的真实雷数必须满足 lb <= 实际 <= 上界
let checked = 0, bad = 0;
for (let t = 0; t < 5; t++) {
  const gg = deal(16, 30, 100);
  if (!gg) continue;
  const bb = gg.board;
  for (const h of shapeHints(bb)) {
    const cells = bb.shapeCells(h.i, h.key);
    const realMines = cells.filter((p) => bb.mines.has(p)).length;
    const hidden = cells.filter((p) => bb.unk(p)).length;
    checked++;
    if (h.exact && realMines !== h.lb) { bad++; console.log(`  精确值不成立: ${h.key} lb=${h.lb} 实际=${realMines}`); }
    if (!h.exact && realMines < h.lb) { bad++; console.log(`  下界不成立: ${h.key} lb=${h.lb} 实际=${realMines}`); }
    if (h.lb > hidden) { bad++; console.log(`  下界超过未开格数: ${h.key} lb=${h.lb} 未开=${hidden}`); }
  }
}
console.log(`提示自证：检查 ${checked} 条，不成立 ${bad} 条`);
