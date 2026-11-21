// Cross-implementation baseline, JavaScript side. See test/java/CrossProbe.java.
//   node test/cross.mjs > build-java/cross-js.jsonl
import { build } from '../lib/core.js';

const CASES = [[9, 9, 10], [16, 16, 40], [16, 30, 100]];
const SEEDS = [1, 2, 3, 7, 12345, 999999, 20261015];

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
