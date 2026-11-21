// README 与代码的一致性检查：难度表顺序/密度/地块数，以及所有被引用的文件。
//   node test/check-readme.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const readme = fs.readFileSync(path.join(root, 'README.md'), 'utf8');
const java = fs.readFileSync(path.join(root, 'src', 'Sweeper.java'), 'utf8');

let bad = 0;
const fail = (msg) => { console.log('  ' + msg); bad++; };

// 1) 代码里的 LEVELS
const block = java.match(/LEVELS = \{([\s\S]*?)\};/);
const levels = [...block[1].matchAll(/\{(\d+), (\d+), (\d+)\}/g)].map((m) => [+m[1], +m[2], +m[3]]);
console.log(`代码里的难度：${levels.length} 档`);

// 2) README 表格是两列布局：每行左一个难度、右一个难度。按行取样，再拼成左块 + 右块。
const rowRe = /^\| (\d+)×(\d+) \| (\d+) \| ([\d.]+)% \| (\d+) \| (\d+)×(\d+) \| (\d+) \| ([\d.]+)% \| (\d+) \|$/gm;
const left = [], right = [], leftDens = [], rightDens = [];
for (const m of readme.matchAll(rowRe)) {
  left.push([+m[1], +m[2], +m[3], +m[5]]);
  leftDens.push(+m[4]);
  right.push([+m[6], +m[7], +m[8], +m[10]]);
  rightDens.push(+m[9]);
}
const fromReadme = [...left, ...right];
const densList = [...leftDens, ...rightDens];
console.log(`README 表格：${fromReadme.length} 档（左 ${left.length} 行 + 右 ${right.length} 行）`);

if (fromReadme.length !== levels.length) fail(`档数不一致：代码 ${levels.length}，README ${fromReadme.length}`);

const n = Math.min(fromReadme.length, levels.length);
for (let i = 0; i < n; i++) {
  const [r, c, m, cells] = fromReadme[i];
  const [R, C, M] = levels[i];
  const where = `第 ${i + 1} 档`;
  if (r !== R || c !== C || m !== M) fail(`${where} 盘面不符：代码 ${R}×${C}·${M}，README ${r}×${c}·${m}`);
  if (cells !== R * C) fail(`${where} 地块数不符：应为 ${R * C}，README 写 ${cells}`);
  const want = +(M / (R * C) * 100).toFixed(1);
  if (Math.abs(densList[i] - want) > 0.05) fail(`${where} 密度不符：应为 ${want}%，README 写 ${densList[i]}%`);
}

// 3) 引用的文件是否存在
const refs = new Set();
for (const m of readme.matchAll(/\]\((?!https?:)([^)#]+)\)/g)) refs.add(m[1]);
for (const m of readme.matchAll(/src="assets\/([^"]+)"/g)) refs.add('assets/' + m[1]);
for (const m of readme.matchAll(/java -cp build-java (\w+)/g)) {
  const cls = m[1];
  // Sweeper 是主类（src/），其余是 test/java 下的探针
  refs.add(cls === 'Sweeper' ? 'src/Sweeper.java' : `test/java/${cls}.java`);
}
refs.delete('');
let checkedRefs = 0;
for (const rel of refs) {
  if (rel.startsWith('http')) continue;
  checkedRefs++;
  if (!fs.existsSync(path.join(root, rel))) fail(`引用的文件不存在：${rel}`);
}
console.log(`检查引用：${checkedRefs} 个`);

console.log(bad === 0 ? 'README 与代码一致' : `${bad} 处问题`);
process.exit(bad === 0 ? 0 : 1);
