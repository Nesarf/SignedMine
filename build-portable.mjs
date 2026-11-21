// 打 Windows x64 便携包：解压即用，目标机器不需要装 Java。
//
//   node build-portable.mjs [--out <目录>]
//
// 需要 JDK 17+，且 javac / jar / jpackage 在 PATH 上。
// 产物：
//   <out>/dist/SignedMine/                          目录形态，双击 SignedMine.exe 即可
//   <out>/SignedMine-<版本>-win-x64-portable.zip     压缩包（Windows 自带 tar 即可打）
//
// 运行时只带 java.base / java.datatransfer / java.xml / java.prefs / java.desktop，
// 体积从完整 JRE 的 128 MB 压到 42 MB 左右。少一个模块 Swing 就起不来，
// 所以这里是按「够用」而不是按「尽量小」取的。
//
// 默认输出到仓库内的 build-portable/（已进 .gitignore）；要留档就传 --out。

import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
const version = pkg.version;

const outIdx = process.argv.indexOf('--out');
const outDir = path.resolve(root, outIdx > 0 && process.argv[outIdx + 1] ? process.argv[outIdx + 1] : 'build-portable');
const dist = path.join(outDir, 'dist');
const app = path.join(dist, 'SignedMine');

const run = (cmd, args) => {
  process.stdout.write(`> ${cmd} ${args.join(' ')}\n`);
  execFileSync(cmd, args, { stdio: 'inherit', cwd: root });
};

fs.rmSync(outDir, { recursive: true, force: true });
fs.mkdirSync(path.join(outDir, 'input'), { recursive: true });

run('javac', ['-encoding', 'UTF-8', '-d', path.join(outDir, 'classes'), path.join('src', 'Sweeper.java')]);
run('jar', ['--create', '--file', path.join(outDir, 'input', 'SignedMine.jar'),
  '--main-class', 'Sweeper', '-C', path.join(outDir, 'classes'), '.']);
run('jpackage', [
  '--type', 'app-image',
  '--name', 'SignedMine',
  '--app-version', version,
  '--vendor', 'Nesarf',
  '--description', 'Minesweeper with shape hints',
  '--input', path.join(outDir, 'input'),
  '--main-jar', 'SignedMine.jar',
  '--main-class', 'Sweeper',
  '--dest', dist,
  '--add-modules', 'java.desktop',
  '--jlink-options', '--strip-debug --no-header-files --no-man-pages --compress=2',
]);

fs.mkdirSync(path.join(app, 'web'), { recursive: true });
fs.copyFileSync(path.join(root, 'web', 'sweeper.html'), path.join(app, 'web', 'sweeper.html'));
fs.copyFileSync(path.join(root, 'packaging', 'README-portable.txt'), path.join(app, 'README.txt'));

const zip = path.join(outDir, `SignedMine-${version}-win-x64-portable.zip`);
run('tar', ['-a', '-cf', zip, '-C', dist, 'SignedMine']);

const walk = (dir) => fs.readdirSync(dir, { withFileTypes: true })
  .reduce((n, e) => n + (e.isDirectory() ? walk(path.join(dir, e.name)) : 1), 0);
const size = (dir) => fs.readdirSync(dir, { withFileTypes: true })
  .reduce((n, e) => n + (e.isDirectory() ? size(path.join(dir, e.name)) : fs.statSync(path.join(dir, e.name)).size), 0);
const mb = (n) => (n / 1048576).toFixed(1);

console.log('');
console.log(`目录    ${path.relative(root, app)}  ${mb(size(app))} MB / ${walk(app)} 个文件`);
console.log(`压缩包  ${path.relative(root, zip)}  ${mb(fs.statSync(zip).size)} MB`);
console.log('双击 SignedMine.exe 运行；web/sweeper.html 用浏览器打开。');
