// 把 lib/core.js 同步进 web/sweeper.html 的内联引擎段。
//
// 网页版是单文件（打开一个 html 就能玩），所以引擎必须内联；
// 于是「一份代码」变成「两份文本 + 一个生成器」。改引擎只改 lib/core.js，
// 然后跑这个脚本；test/check-html.mjs 会在两边不一致时报错。
//
// 换行风格跟随目标文件：仓库里这份 html 是 CRLF，生成器若直接吐 LF，
// 每次同步都会顺手改出一大片无关的换行差异。
//
// 用法：node test/sync-inline.mjs [--check]
//   --check  只比对，不写入；不一致时退出码 1
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const CORE = path.join(root, 'lib', 'core.js');
const HTML = path.join(root, 'web', 'sweeper.html');

export const BEGIN = '// ===== ENGINE BEGIN：由 lib/core.js 生成，勿手改（node test/sync-inline.mjs）=====';
export const END = '// ===== ENGINE END =====';

// 引擎在 HTML 里是普通脚本，没有 module 语义，所以去掉 export 前缀，其余逐字保留。
export function engineText(eol = '\n') {
  const src = fs.readFileSync(CORE, 'utf8').replace(/\r\n/g, '\n').replace(/^export /gm, '');
  return `${BEGIN}\n${src.trimEnd()}\n${END}`.split('\n').join(eol);
}

export function splitHtml(html) {
  const eol = html.includes('\r\n') ? '\r\n' : '\n';
  const lines = html.split(/\r?\n/);
  const open = lines.findIndex((l) => l.trim() === '<script>');
  if (open < 0) throw new Error('找不到第一个 <script>');
  const close = lines.findIndex((l, i) => i > open && l.trim() === '</script>');
  if (close < 0) throw new Error('找不到第一个 </script>');
  return { head: lines.slice(0, open + 1), tail: lines.slice(close), eol };
}

export function patchedHtml() {
  const html = fs.readFileSync(HTML, 'utf8');
  const { head, tail, eol } = splitHtml(html);
  return { text: [...head, engineText(eol), ...tail].join(eol), html };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const check = process.argv.includes('--check');
  const { text, html } = patchedHtml();
  if (check) {
    if (html === text) { console.log('内联引擎与 lib/core.js 一致'); process.exit(0); }
    console.log('内联引擎与 lib/core.js 不一致，跑 node test/sync-inline.mjs 重新生成');
    process.exit(1);
  }
  fs.writeFileSync(HTML, text, 'utf8');
  console.log(`已同步：${path.relative(root, HTML)}（引擎 ${engineText().split('\n').length} 行）`);
}
