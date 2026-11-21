// Compare the two implementations' baselines line by line.
//   java -cp build-java CrossProbe      > build-java/cross-java.jsonl
//   node test/cross.mjs                 > build-java/cross-js.jsonl
//   node test/cross-check.mjs
//
// What is compared: ok, mine layout, opening position. Those depend only on the RNG and
// open(), which now share one algorithm (mulberry32) on both sides.
// hintCount is printed for information, not asserted: tie-breaking inside pickCandidate can
// differ because the two sides iterate cells in different orders.
import fs from 'node:fs';

const read = (p) => fs.readFileSync(p, 'utf8').trim().split(/\r?\n/).filter(Boolean).map((l) => JSON.parse(l));

let java, js;
try {
  java = read('build-java/cross-java.jsonl');
  js = read('build-java/cross-js.jsonl');
} catch (e) {
  console.log(`读不到基线文件：${e.message}`);
  console.log('先跑：java -cp build-java CrossProbe > build-java/cross-java.jsonl');
  console.log('     node test/cross.mjs > build-java/cross-js.jsonl');
  process.exit(1);
}

if (java.length !== js.length) {
  console.log(`条数不同：java ${java.length} / js ${js.length}`);
  process.exit(1);
}

const eq = (x, y) => JSON.stringify(x) === JSON.stringify(y);
let badOk = 0, badMines = 0, badSnap = 0, badHints = 0;
const detail = [];
for (let i = 0; i < java.length; i++) {
  const a = java[i], b = js[i];
  if (a.case !== b.case || a.seed !== b.seed) { detail.push(`第 ${i} 行 case/seed 对不上`); continue; }
  if (a.ok !== b.ok) { badOk++; detail.push(`${a.case} seed ${a.seed}：ok 不一致 ${a.ok} / ${b.ok}`); }
  if (!eq(a.mines, b.mines)) { badMines++; detail.push(`${a.case} seed ${a.seed}：雷位不同（${a.mines.length} / ${b.mines.length} 颗）`); }
  if (!eq(a.snapshot, b.snapshot)) { badSnap++; detail.push(`${a.case} seed ${a.seed}：开局局面不同`); }
  if (!eq(a.hints, b.hints)) { badHints++; detail.push(`${a.case} seed ${a.seed}：提示序列不同（${a.hints.length} / ${b.hints.length} 条）`); }
}
console.log(`用例 ${java.length} 条`);
console.log(`  ok 不一致      ${badOk}`);
console.log(`  雷位不同       ${badMines}`);
console.log(`  开局局面不同   ${badSnap}`);
console.log(`  提示序列不同   ${badHints}`);
for (const d of detail.slice(0, 6)) console.log('  ' + d);

const fail = badOk + badMines + badSnap + badHints;
console.log(fail === 0 ? '雷位、开局与提示序列完全一致' : `${fail} 项不一致`);
process.exit(fail === 0 ? 0 : 1);
