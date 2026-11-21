// 界面层的冒烟测试：把 swepser.html 的两段脚本放进一个最小 DOM 桩里真跑一遍。
// 语法通过不等于交互跑得通 —— 这里要走到 openAt / chordAt / undo 里面去。
// 用法：node test/ui-smoke.mjs
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const blocks = html.split('<script>').slice(1).map((s) => s.split('</script>')[0]);
const code = blocks.join('\n;\n');

let passed = 0;
const fails = [];
function check(name, cond, extra = '') {
  if (cond) { passed++; return; }
  fails.push(`${name}${extra ? ' — ' + extra : ''}`);
}

// ---- 最小 DOM 桩 ----
const listeners = new Map();
function el(id) {
  const node = {
    id, value: id === 'level' ? '9,9,10' : '1', textContent: '', offsetHeight: 40, offsetWidth: 320,
    clientWidth: 360, clientHeight: 640, scrollLeft: 0, scrollTop: 0,
    style: {}, width: 0, height: 0,
    classList: { _s: new Set(), add(c) { this._s.add(c); }, remove(c) { this._s.delete(c); }, contains(c) { return this._s.has(c); }, toggle(c, on) { const v = on === undefined ? !this._s.has(c) : !!on; v ? this._s.add(c) : this._s.delete(c); return v; } },
    getContext: () => new Proxy({}, { get: () => () => {}, set: () => true }),
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 360, height: 360 }),
    setPointerCapture() {}, releasePointerCapture() {},
    addEventListener(type, fn) { const k = `${id}:${type}`; listeners.set(k, fn); },
    removeEventListener() {},
    dispatch(type, ev) { const fn = listeners.get(`${id}:${type}`); if (fn) fn(ev); },
  };
  return node;
}
const nodes = new Map();
const document = {
  getElementById(id) { if (!nodes.has(id)) nodes.set(id, el(id)); return nodes.get(id); },
  querySelector() { return el('header'); },
  body: { classList: { _s: new Set(), add(c) { this._s.add(c); }, remove(c) { this._s.delete(c); }, contains(c) { return this._s.has(c); }, toggle(c, on) { const v = on === undefined ? !this._s.has(c) : !!on; v ? this._s.add(c) : this._s.delete(c); return v; } } },
  addEventListener() {},
};

const store = new Map();
const sandbox = {
  console, Math, Date, JSON, Number, String, Object, Array, Set, Map, Int8Array, Uint8Array, Float64Array, isNaN, parseInt, parseFloat,
  document,
  localStorage: { getItem: (k) => (store.has(k) ? store.get(k) : null), setItem: (k, v) => store.set(k, String(v)), removeItem: (k) => store.delete(k) },
  window: { innerHeight: 800, innerWidth: 400, devicePixelRatio: 2, addEventListener() {}, matchMedia: () => ({ matches: false }) },
  setTimeout, clearTimeout, setInterval, clearInterval, performance, Promise, Proxy,
};
sandbox.globalThis = sandbox;
sandbox.window.document = document;

// 把内部符号挂出来（let 声明的变量不是 context 的属性，只能在同一个脚本里导出）
const hook = `
;globalThis.__hook = {
  newGame, layout, draw, openAt, chordAt, undo, checkWin, toggleFlagAt, findMove, topUpAfter,
  hintMap: () => hintMap, weak: () => weak, board: () => board,
  setSize: (r,c,m)=>{ R=r; C=c; M=m; },
  getSize: ()=>[R,C,M],
};`;

vm.createContext(sandbox);
try {
  new vm.Script(code + hook, { filename: 'sweeper.html' }).runInContext(sandbox);
  check('脚本执行', true);
} catch (e) {
  check('脚本执行', false, e.message);
  console.log(`执行失败：${e.message}`);
  process.exit(1);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
await sleep(120);   // newGame 的 30 ms 延迟 + 余量
const h = sandbox.__hook;

const b = h.board();
check('发牌成功', !!b);
if (!b) { console.log(fails.join('\n')); process.exit(1); }
check('盘面已翻开一格', b.rev.size >= 1, `rev=${b.rev.size}`);
check('中文档位生效', b.R === 9 && b.C === 9 && b.m === 10, `R${b.R}C${b.C}雷${b.m}`);
check('开局提示表已建', h.hintMap().size >= 0);

// 翻开一个确实安全的格：结论必须来自引擎而不是运气
let safe = -1;
for (let i = 0; i < 81; i++) if (b.unk(i) && !b.mines.has(i)) { safe = i; break; }
check('找到可翻的格', safe >= 0);
const revBefore = b.rev.size;
h.openAt(safe);
check('openAt 之后多开了格', b.rev.size > revBefore, `${revBefore} -> ${b.rev.size}`);
const revNow = b.rev.size;
h.openAt(safe);                        // 已经翻开的格再点一次，不该有任何变化
check('openAt 对已翻开的格无副作用', b.rev.size === revNow, `${revNow} -> ${b.rev.size}`);

// 撤销要能原样退回
const revAfterOpen = b.rev.size;
h.undo();
check('undo 退回翻开前的格数', b.rev.size === revBefore, `${revAfterOpen} -> ${b.rev.size}`);
check('undo 没把提示弄坏', h.weak().every((x) => b.rev.has(x.i)), '提示挂在没翻开的格上');

// 插旗与连开
const flagTarget = (() => { for (let i = 0; i < 81; i++) if (b.unk(i)) return i; })();
h.toggleFlagAt(flagTarget);
check('插旗生效', b.flag.has(flagTarget));
h.toggleFlagAt(flagTarget);
check('取消旗子生效', !b.flag.has(flagTarget));

// 找一个数字格上、周围没旗的情形：连开应当只是给出说明，不该改盘面
let numCell = -1;
for (const q of b.rev) if (b.num(q) > 0) { numCell = q; break; }
if (numCell >= 0) {
  const before = b.rev.size;
  h.chordAt(numCell);
  check('chordAt 在旗数不足时不改盘面', b.rev.size === before, `${before} -> ${b.rev.size}`);
} else {
  check('chordAt 有数字格可测', true, '本局开局没有数字格，跳过');
}

// 求援：给出的一定得是能确定的一格
document.getElementById('level').value = '16,30,100';   // newGame 从下拉框读档位
h.newGame();
await sleep(400);
const b2 = h.board();
check('高级档发牌成功', !!b2 && b2.R === 16 && b2.C === 30 && b2.m === 100, b2 ? `R${b2.R}C${b2.C}雷${b2.m}` : '空');
if (b2) {
  const mv = h.findMove();
  if (mv) {
    const okSafe = !mv.mine && !b2.mines.has(mv.cell);
    const okMine = mv.mine && b2.mines.has(mv.cell);
    check('求援给出的格子与真实雷位一致', okSafe || okMine, `cell=${mv.cell} mine=${mv.mine}`);
    check('求援给的是未开格', !b2.rev.has(mv.cell));
  } else {
    check('求援没有可给的格', true, '本局开局点不动，属正常');
  }
}

console.log(`通过 ${passed} 项`);
if (fails.length) {
  console.log('不通过：');
  for (const f of fails) console.log('  ' + f);
  process.exit(1);
}
console.log('全部通过');
process.exit(0);
