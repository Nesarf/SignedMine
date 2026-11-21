// 新旧引擎的发牌成功率与耗时对比：改完之后失败率到底升了还是降了。
// 用法：node test/compare-orig.mjs [局数]
import { deal as dealNew } from '../lib/core.js';
import { deal as dealOld } from './core-orig.mjs';

const N = Number(process.argv[2] || 120);

function run(label, deal, R, C, M) {
  const times = [];
  let fail = 0;
  for (let i = 0; i < N; i++) {
    const t0 = performance.now();
    const r = deal(R, C, M);
    times.push(performance.now() - t0);
    if (!r) fail++;
  }
  times.sort((a, b) => a - b);
  const avg = (times.reduce((a, b) => a + b, 0) / times.length).toFixed(1);
  const p90 = times[Math.floor(times.length * 0.9)].toFixed(1);
  console.log(`  ${label.padEnd(8)} 失败 ${String(fail).padStart(3)}/${N}（${(fail / N * 100).toFixed(1)}%） 平均 ${avg.padStart(6)} ms  p90 ${p90.padStart(6)} ms`);
}

for (const [R, C, M] of [[16, 16, 40], [16, 30, 100]]) {
  console.log(`${R}x${C}/${M}`);
  run('原版', dealOld, R, C, M);
  run('新版', dealNew, R, C, M);
}
