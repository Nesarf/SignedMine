// 大样本：发牌成功率与耗时分布。判断失败开局会不会真的落到玩家手里。
// 用法：node test/deal-stats.mjs [局数]
import { deal } from '../lib/core.js';

const N = Number(process.argv[2] || 200);
for (const [R, C, M] of [[9, 9, 10], [16, 16, 40], [16, 30, 100]]) {
  const times = [];
  let fail = 0;
  for (let i = 0; i < N; i++) {
    const t0 = performance.now();
    const r = deal(R, C, M);
    times.push(performance.now() - t0);
    if (!r) fail++;
  }
  times.sort((a, b) => a - b);
  const pct = (p) => times[Math.min(times.length - 1, Math.floor(times.length * p))].toFixed(1);
  const avg = (times.reduce((a, b) => a + b, 0) / times.length).toFixed(1);
  console.log(`${R}x${C}/${M}  ${N} 局：失败 ${fail}  平均 ${avg} ms  中位 ${pct(0.5)}  p90 ${pct(0.9)}  p99 ${pct(0.99)}  最慢 ${times[times.length - 1].toFixed(1)}`);
}
