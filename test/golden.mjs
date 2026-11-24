// 行为定格：优化前后用同一批 seed 必须产生完全相同的局。
// 用法：node test/golden.mjs > test/golden.json   然后优化后：node test/golden.mjs | diff
import { build, shapeHints } from '../lib/core.js';

const SEEDS = [1, 2, 3, 7, 12345, 999999, 20261015];
const CASES = [[9, 9, 10], [16, 16, 40], [16, 30, 100]];

const out = [];
for (const [R, C, M] of CASES) {
  for (const seed of SEEDS) {
    const r = build(R, C, M, seed);
    const b = r.board;
    out.push({
      case: `${R}x${C}/${M}`,
      seed,
      ok: r.ok,
      rev: [...b.rev].sort((x, y) => x - y),
      mines: [...b.mines].sort((x, y) => x - y),
      snapshot: [...r.snapshot].sort((x, y) => x - y),
      hints: r.hints.map((h) => `${h.i}:${h.key}:${h.lb}`),
      shape: shapeHints(b).map((h) => `${h.i}:${h.key}:${h.lb}:${h.exact ? 'e' : 'l'}`),
    });
  }
}
process.stdout.write(JSON.stringify(out, null, 1));
