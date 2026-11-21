# SignedMine

带「形状提示」的扫雷。一套引擎，几种形态：Swing 桌面版、单文件网页（兼作 APK），
以及一个 dsh-go 面板。

规则还是经典扫雷，多出来的是提示：每个已翻开的数字格上最多挂一条形状提示，
告诉你某个八邻域形状里有几颗雷，而且**这条提示恒成立**，不会随局面变化而失效。

## 形态

| 形态 | 入口 | 说明 |
| --- | --- | --- |
| Swing 桌面版 | `src/Sweeper.java` | 单文件无依赖，最完整的交互形态 |
| 单文件网页 | `android/assets/sweeper.html` | 浏览器直接打开；`android/` 是它的 APK 壳 |
| dsh-go 面板 | `lib/index.js` | 面板只做展示：发一局 9×9、画字符盘、报提示条数 |
| 引擎 | `lib/core.js` | 发牌、形状提示、求援与求解；**其余各形态都以它为准** |
| Python 参考 | `src/saolei.py`、`src/ui_sweeper.py`、`src/sweeper_core.py` | 早期实现，提示口径未同步，仅作算法参考 |

## 快速开始

桌面版（JDK 17+）：

```sh
javac -encoding UTF-8 src/Sweeper.java
java Sweeper
```

网页版：直接用浏览器打开 `android/assets/sweeper.html`，不需要服务器。

插件（需要 dsh-go 宿主）：

```sh
dsh plugin --profile web add .
```

插件 id 仍然是 `local-sweeper`，与它作为 dgupb 插件包时的 id 一致，装过的不会断。

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

形状内雷数的可证明区间是 `[剩余雷数 - 形状外未开邻格数, 剩余雷数]`，
下界还可以被每个已翻开邻居的剩余雷数继续抬升。只有上下界相等时才写「共有」，
所以两种提示都恒成立。四档显示（精简 / 标准 / 全部 / 关闭）、操作方式、
构建步骤和已知限制都在 [GAME.md](GAME.md) 里。

## 引擎与内联副本

单文件是网页形态的设计约束（APK 里只装一个 html），所以引擎必须内联；
于是「一份代码」变成「两份文本 + 一个生成器」：

```sh
node test/sync-inline.mjs          # 把 lib/core.js 生成进 html 的内联引擎段
node test/sync-inline.mjs --check  # 只比对，不一致时退出码 1
```

改引擎只改 `lib/core.js`，然后跑生成器；`test/check-html.mjs` 会在两边漂移时报错。

## 测试

不需要第三方依赖，JS 与 Java 各一套：

```sh
# 引擎与单文件应用
node test/check-html.mjs      # 两段脚本能否解析 + 内联引擎是否与 core.js 一致
node test/ui-smoke.mjs        # 最小 DOM 桩里真跑 openAt / chordAt / undo / findMove
node test/golden.mjs          # 行为基线（与 test/golden.json 对比可查回归）
node test/bench.mjs           # 发牌与中局重算的绝对耗时
node test/deal-stats.mjs 200  # 发牌成功率与耗时分布
node test/build-rate.mjs      # 单次 build 的成功率，决定重试次数
node test/compare-orig.mjs    # 与仓库原始引擎（test/core-orig.mjs）逐项对比
node test/cross.mjs           # 输出 JS 侧基线（与 Java 的 CrossProbe 同格式）
node test/cross-check.mjs     # 两侧基线比对：ok / 雷位 / 开局局面 / 提示序列

# Java 桌面版
javac -encoding UTF-8 -d build-java src/Sweeper.java
javac -encoding UTF-8 -cp build-java -d build-java test/java/EngineProbe.java
java -cp build-java EngineProbe 30        # 发牌成功率/耗时、提示自证、中局重算耗时
java -cp build-java IncrementalProbe      # 增量补算出的提示在推进中是否仍恒成立
java -cp build-java ExactDump             # 打印一条「精确值不成立」提示的中间量
java -cp build-java Sweeper shot out.png  # 无头截图，顺带确认界面能起来
```

## 构建 APK

```sh
cd android
./build.sh
adb install -r build/sweeper.apk
```

`build.sh` 是给 Termux 环境写的（`SDK=/opt/android-sdk`、`/usr/bin/aapt2`，
末尾会 `cp` 到 `/sdcard/Download/DSH-Go/`），换平台要先改这几个路径。

## 目录

```text
SignedMine/
  lib/            core.js（引擎）、index.js（dsh-go 面板）
  src/            Sweeper.java、早期 Python 实现
  android/        单文件网页 + APK 工程
  test/           JS 回归脚本、Java 探测（test/java/）
  GAME.md         玩法、提示语义、档位、操作、构建、已知限制
```

## 许可证

MIT，见 [LICENSE](LICENSE)。
