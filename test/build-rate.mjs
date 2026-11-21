// 单次 build 的成功率与耗时：deal 的失败率由它决定。
// 用法：node test/build-rate.mjs [次数]
import { build } from '../lib/core.js';

const N = Number(process.argv[2] || 300);
for (const [R, C, M] of [[16, 16, 40], [16, 30, 100]]) {
  let ok = 0, okMs = 0, failMs = 0;
  const t0 = performance.now();
  for (let i = 0; i < N; i++) {
    const s = performance.now();
    const r = build(R, C, M, (Math.random() * 1e9) | 0);
    if (r.ok) { ok++; okMs += performance.now() - s; } else failMs += performance.now() - s;
  }
  const wall = performance.now() - t0;
  const p = ok / N;
  // 60 次重试全都失败的概率
  const dealFail = Math.pow(1 - p, 60);
  console.log(`${R}x${C}/${M}：单次成功 ${ok}/${N} = ${(p * 100).toFixed(2)}%  失败局平均 ${(failMs / Math.max(1, N - ok)).toFixed(2)} ms  成功局平均 ${(okMs / Math.max(1, ok)).toFixed(1)} ms  总墙钟 ${wall.toFixed(0)} ms`);
  console.log(`  推算 deal(60 次重试) 失败率 ${(dealFail * 100).toFixed(2)}%  平均重试 ${(1 / p).toFixed(1)} 次`);
}
