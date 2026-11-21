// 单文件应用的自检：两段脚本能不能解析、内联引擎是否与 lib/core.js 一致。
// 用法：node test/check-html.mjs
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import { engineText, patchedHtml } from './sync-inline.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const HTML = path.join(root, 'web', 'sweeper.html');
const html = fs.readFileSync(HTML, 'utf8');

const blocks = html.split('<script>').slice(1).map((s) => s.split('</script>')[0]);
let bad = 0;

if (blocks.length !== 2) {
  console.log(`脚本块数量不对：期望 2，实际 ${blocks.length}`);
  bad++;
}

blocks.forEach((code, i) => {
  try {
    new vm.Script(code, { filename: `sweeper.html#script${i}` });
    console.log(`第 ${i + 1} 段脚本：语法通过（${code.split('\n').length} 行）`);
  } catch (e) {
    console.log(`第 ${i + 1} 段脚本：语法不通过 —— ${e.message}`);
    bad++;
  }
});

// 内联引擎与 lib/core.js 的一致性（整文件比对：连换行风格一起算）
const { text: expected } = patchedHtml();
if (html === expected) {
  console.log(`内联引擎：与 lib/core.js 一致（${engineText().split('\n').length} 行）`);
} else {
  console.log('内联引擎：与 lib/core.js 不一致，跑 node test/sync-inline.mjs');
  bad++;
}

// 之前 pointercancel 注册了两次（内容不同、后一个只清定时器），重复注册是无声的 bug 温床
const dup = (html.match(/addEventListener\('pointercancel'/g) || []).length;
if (dup <= 1) {
  console.log('pointercancel 监听：一次');
} else {
  console.log(`pointercancel 监听：注册了 ${dup} 次`);
  bad++;
}

if (bad) { console.log(`\n${bad} 项不通过`); process.exit(1); }
console.log('\n全部通过');
