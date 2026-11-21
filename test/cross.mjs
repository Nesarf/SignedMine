// Cross-implementation baseline, JavaScript side. See test/java/CrossProbe.java.
//   node test/cross.mjs > build-java/cross-js.jsonl
import { build } from '../lib/core.js';

const CASES = [[9, 9, 10], [16, 16, 40], [16, 30, 100]];
// seed 列表：--bulk N 生成 N 个（与 CrossProbe 的 --bulk 同一规则）；
// 直接给数字就用这些数字；两者都不给用下面这 7 个固定种子。
let SEEDS;
if (process.argv[2] === '--bulk') {
  const n = Number(process.argv[3] || 200);
  SEEDS = Array.from({ length: n }, (_, i) => (i * 7919 + 12345) % 1000000);
} else if (process.argv.length > 2) {
  SEEDS = process.argv.slice(2).map(Number);
} else {
  SEEDS = [1, 2, 3, 7, 12345, 999999, 20261015];
}

for (const [R, C, M] of CASES) {
  for (const seed of SEEDS) {
    const r = build(R, C, M, seed);
    const b = r.board;
    const mines = [...b.mines].sort((x, y) => x - y);
    const snapshot = [...r.snapshot].sort((x, y) => x - y);
    console.log(JSON.stringify({
      case: `${R}x${C}/${M}`,
      seed,
      ok: r.ok,
      mines: r.ok ? mines : [],
      snapshot: r.ok ? snapshot : [],
      hints: r.hints.map((h) => [h.i, h.key, h.lb]),
    }));
  }
}
