# SignedMine

带「形状提示」的扫雷，纯桌面：一个 Swing 桌面版，一个单文件网页版（浏览器打开即玩）。

规则还是经典扫雷，多出来的是提示：每个已翻开的数字格上最多挂一条形状提示，
告诉你某个八邻域形状里有几颗雷，而且**这条提示恒成立**，不会随局面变化而失效。

## 形态

| 形态 | 入口 | 说明 |
| --- | --- | --- |
| Swing 桌面版 | `src/Sweeper.java` | 单文件无依赖，主力形态 |
| 单文件网页 | `web/sweeper.html` | 浏览器直接打开，不需要服务器 |
| 引擎 | `lib/core.js` | 发牌、形状提示、求援与求解 |

两种形态共用同一套算法：同一个 seed 会发出同一局、选出同一串提示（见「交叉验证」）。

## 快速开始

桌面版（JDK 17+）：

```sh
javac -encoding UTF-8 src/Sweeper.java
java Sweeper
```

网页版：用浏览器打开 `web/sweeper.html`，不需要服务器。

## 形状提示

八邻域编号（左上 1，右下 8）：

```text
 1 2 3
 4   5
 6 7 8
```

一个数字格上最多挂一条提示：

```text
右上角  至少 n 颗   该形状里至少有 n 颗雷
右下角  共有 n 颗   该形状里恰好有 n 颗雷
```

形状内雷数的可证明区间是 `[下界, 剩余雷数]`，下界由「形状外未开邻格数」与
「每个已翻开邻居的剩余雷数」两条推导取较大者。只有下界等于剩余雷数时才写「共有」，
所以两种提示都恒成立。四档显示、操作方式与已知限制都在 [GAME.md](GAME.md) 里。

## 引擎与内联副本

网页版是单文件，所以引擎必须内联；于是「一份代码」变成「两份文本 + 一个生成器」：

```sh
node test/sync-inline.mjs          # 把 lib/core.js 生成进 web/sweeper.html 的内联引擎段
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
java -cp build-java EngineProbe 30        # 发牌成功率/耗时、提示自证、中局重算耗时
java -cp build-java IncrementalProbe      # 增量补算出的提示在推进中是否仍恒成立
java -cp build-java ExactDump             # 打印一条「精确值不成立」提示的中间量
java -cp build-java Sweeper shot out.png  # 无头截图，顺带确认界面能起来
```

## 交叉验证

两套实现共用同一支随机数（mulberry32）、同一套上下界判定，并且都按格号升序遍历。
所以同一个 seed 必须发出同一局、选出同一串提示：

```sh
java -cp build-java CrossProbe --bulk 200 | Out-File -Encoding utf8 build-java/cross-java.jsonl
node test/cross.mjs --bulk 200 | Out-File -Encoding utf8 build-java/cross-js.jsonl
node test/cross-check.mjs   # 比 ok / 雷位 / 开局局面 / 提示序列
```

当前基线：3 档 × 200 个 seed = 600 条，四项全等。

## 打包便携版

```sh
node build-portable.mjs          # 产物在 build-portable/（已进 .gitignore）
node build-portable.mjs --out D:\dist
```

需要 JDK 17+，`javac` / `jar` / `jpackage` 在 PATH 上。产出两个东西：

- `dist/SignedMine/` —— 目录形态，双击 `SignedMine.exe` 即可，运行时在 `runtime/` 里
- `SignedMine-<版本>-win-x64-portable.zip` —— 压缩包，解压即用

运行时只带 `java.base` / `java.datatransfer` / `java.xml` / `java.prefs` / `java.desktop`
五个模块，从完整 JRE 的 128 MB 压到 42 MB 左右。少一个模块 Swing 就起不来，
所以这是按「够用」取的，不是按「尽量小」。

## 目录

```text
SignedMine/
  lib/core.js             引擎
  src/Sweeper.java        Swing 桌面版
  web/sweeper.html        单文件网页版
  test/                   回归脚本、Java 探测（test/java/）、行为基线
  packaging/              便携包里的说明文件
  build-portable.mjs      便携包打包脚本
  GAME.md                 玩法、提示语义、档位、操作、测试、已知限制
```

## 许可证

MIT，见 [LICENSE](LICENSE)。
