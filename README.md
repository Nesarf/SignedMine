# SignedMine

带「形状提示」的扫雷。纯桌面：一个 Swing 桌面版，一个单文件网页版。

[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![java](https://img.shields.io/badge/java-17%2B-orange.svg)](src/Sweeper.java)
[![dependencies](https://img.shields.io/badge/dependencies-none-brightgreen.svg)]()
[![play in browser](https://img.shields.io/badge/play-in%20browser-39c5bb.svg)](https://nesarf.github.io/SignedMine/)

![16×30 · 100 雷，全部提示](assets/screenshot.png)

不用下载也能玩：[**打开网页版**](https://nesarf.github.io/SignedMine/)

## 什么是形状提示

经典扫雷的每个数字格告诉你周围有几颗雷。这里多给一条：每个已翻开的数字格上挂着
一个形状与一个小数字，说的是「这个形状里有几颗雷」。

```text
 1 2 3
 4   5      右上角  至少 n 颗   该形状里至少有 n 颗雷
 6 7 8      右下角  共有 n 颗   该形状里恰好有 n 颗雷
```

可证明的区间是「形状内雷数落在 [下界, 剩余雷数]」：下界由「形状外未开邻格数」与
「每个已翻开邻居的剩余雷数」两条推导取较大者，只有下界等于剩余雷数时才写「共有」。
所以两种提示都**恒成立**，不会随局面变化而失效——这是这个游戏唯一特别的地方。

## 难度

24 档，密度从 12% 到 21%，地块从 25 到 3000。每档实测 20 局零失败；
2500 格以上的三档各测 6 局，也是零失败。

| 盘面 | 雷 | 密度 | 地块 | 盘面 | 雷 | 密度 | 地块 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 5×5 | 3 | 12.0% | 25 | 16×24 | 70 | 18.2% | 384 |
| 6×6 | 5 | 13.9% | 36 | 16×30 | 99 | 20.6% | 480 |
| 7×7 | 7 | 14.3% | 49 | 16×30 | 100 | 20.8% | 480 |
| 8×8 | 9 | 14.1% | 64 | 20×20 | 80 | 20.0% | 400 |
| 9×9 | 10 | 12.3% | 81 | 20×30 | 120 | 20.0% | 600 |
| 10×10 | 14 | 14.0% | 100 | 24×30 | 145 | 20.1% | 720 |
| 12×12 | 20 | 13.9% | 144 | 24×36 | 170 | 19.7% | 864 |
| 12×12 | 26 | 18.1% | 144 | 30×30 | 190 | 21.1% | 900 |
| 14×14 | 30 | 15.3% | 196 | 30×40 | 240 | 20.0% | 1200 |
| 16×16 | 40 | 15.6% | 256 | 40×63 | 500 | 19.8% | 2520 |
| 16×16 | 51 | 19.9% | 256 | 50×50 | 500 | 20.0% | 2500 |
| 16×20 | 60 | 18.8% | 320 | 60×50 | 600 | 20.0% | 3000 |

最后三档从 2500 格起，一屏放不下，棋盘放在滚动区里看；
50×50 的发牌要 2.5 秒左右（界面上会先显示「生成中…」）。

密度再往 23% 以上走（例如 20×30 · 140），单次 build 的成功率会掉到一半以下，
发牌要重试很久，所以没有收入。

格距按可用宽度算，字号跟着格子走，小盘面居中、大盘面滚动，窗口缩放即时重排。

## 档位

| 档位 | 显示什么 |
| --- | --- |
| 精简 | 非平凡的精确值（0 < n < 形状格数） |
| 标准 | 再加「至少 2 颗」的弱提示 |
| 全部 | 每个数字格都挂一条，含「至少 0 颗」——`←0` 的意思就是这格暂时没有结论 |

## 两种形态

| 形态 | 入口 | 说明 |
| --- | --- | --- |
| Swing 桌面版 | [`src/Sweeper.java`](src/Sweeper.java) | 单文件无依赖，`javac` 一下就能跑 |
| 单文件网页 | [`index.html`](index.html) | 浏览器打开即玩，也托管在 Pages 上 |
| 引擎 | [`lib/core.js`](lib/core.js) | 发牌、形状提示、求援与求解 |

两种形态共用同一套算法：同一个 seed 会发出同一局、选出同一串提示（见「交叉验证」）。

## 快速开始

桌面版（JDK 17+）：

```sh
javac -encoding UTF-8 src/Sweeper.java
java Sweeper
```

网页版：浏览器打开 [`index.html`](index.html)，或者直接访问
[nesarf.github.io/SignedMine](https://nesarf.github.io/SignedMine/)。

## 操作

| 动作 | 鼠标 | 键盘 |
| --- | --- | --- |
| 翻开 | 左键 | `空格` |
| 插旗 | 右键 | `F` |
| 连开周围 | 中键，或双击数字 | `空格`（光标在已翻开的数字格上） |
| 移动光标 | — | 方向键 |

## 引擎与内联副本

网页版是单文件（打开一个 html 就能玩），所以引擎必须内联；于是「一份代码」变成
「两份文本 + 一个生成器」：

```sh
node test/sync-inline.mjs          # 把 lib/core.js 生成进 index.html 的内联引擎段
node test/sync-inline.mjs --check  # 只比对，不一致时退出码 1
```

改引擎只改 `lib/core.js`，然后跑生成器；`test/check-html.mjs` 会在两边漂移时报错。

## 测试

不需要第三方依赖，JS 与 Java 各一套：

```sh
# 引擎与网页版
node test/check-html.mjs         # 两段脚本能否解析 + 内联引擎是否与 core.js 一致
node test/ui-smoke.mjs           # 最小 DOM 桩里真跑 openAt / chordAt / undo / findMove
node test/golden.mjs             # 行为基线（与 test/golden.json 对比可查回归）
node test/bench.mjs              # 发牌与中局重算的绝对耗时
node test/deal-stats.mjs 200     # 发牌成功率与耗时分布
node test/build-rate.mjs         # 单次 build 的成功率，决定重试次数
node test/compare-orig.mjs       # 与仓库原始引擎（test/core-orig.mjs）逐项对比

# Java 桌面版
javac -encoding UTF-8 -d build-java src/Sweeper.java
javac -encoding UTF-8 -cp build-java -d build-java test/java/EngineProbe.java
java -cp build-java EngineProbe 30          # 发牌成功率/耗时、提示自证、中局重算耗时
java -cp build-java IncrementalProbe        # 增量补算出的提示在推进中是否仍恒成立
java -cp build-java ExactDump               # 打印一条「精确值不成立」提示的中间量
java -cp build-java Sweeper shot out.png 2 14  # 截图：档位 2（全部）、难度 14（16×30·100）
```

## 交叉验证

两套实现共用同一支随机数（mulberry32）、同一套上下界判定，并且都按格号升序遍历。
所以同一个 seed 必须发出同一局、选出同一串提示：

```sh
java -cp build-java CrossProbe --bulk 200 | Out-File -Encoding utf8 build-java/cross-java.jsonl
node test/cross.mjs --bulk 200 | Out-File -Encoding utf8 build-java/cross-js.jsonl
node test/cross-check.mjs   # 比 ok / 雷位 / 开局局面 / 提示序列
```

当前基线：3 个尺寸（9×9 / 16×16 / 16×30）× 200 个 seed = 600 条，四项全等。

## 成绩

每条难度的最佳成绩单独记，落在 `HKCU\Software\JavaSoft\Prefs\SignedMine`，
写完立即 flush；网页版用 `localStorage`。桌面版状态栏通关时显示「最佳 n 秒」。

```sh
java -cp build-java PrefProbe              # 现有成绩
java -cp build-java PrefSet 16 30 100 675  # 手工写一条
java -cp build-java PrefClean              # 清掉探针留下的键
```

## 打包便携版

```sh
node build-portable.mjs          # 产物在 build-portable/（已进 .gitignore）
node build-portable.mjs --out D:\dist
```

需要 JDK 17+，`javac` / `jar` / `jpackage` 在 PATH 上。产出两个东西：

- `dist/SignedMine/` —— 目录形态，双击 `SignedMine.exe` 即可，运行时在 `runtime/` 里
- `SignedMine-<版本>-win-x64-portable.zip` —— 压缩包，解压即用

运行时只带 `java.base` / `java.datatransfer` / `java.xml` / `java.prefs` / `java.desktop`
五个模块，从完整 JRE 的 128 MB 压到 42 MB 左右。

## 目录

```text
SignedMine/
  lib/core.js             引擎
  src/Sweeper.java        Swing 桌面版
  index.html              单文件网页版
  assets/screenshot.png   预览图
  test/                   回归脚本、Java 探测（test/java/）、行为基线
  packaging/              便携包里的说明文件
  build-portable.mjs      便携包打包脚本
  GAME.md                 玩法、提示语义、难度、档位、操作、测试、已知限制
```

## 版本

版本号固定为 `0.3.25`：修正直接并入，不再递增。`package.json`、便携包的 `README.txt`
与 release 上的资产用同一个号，重新构建就覆盖。

## 许可证

MIT，见 [LICENSE](LICENSE)。
