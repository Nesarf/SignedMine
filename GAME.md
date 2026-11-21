# 扫雷 · 形状提示版

## 文件

| 路径 | 作用 |
| --- | --- |
| `android/assets/sweeper.html` | 单文件应用（HTML + CSS + JS），浏览器直接打开，也是 APK 里装的那一份 |
| `lib/core.js` | 引擎唯一权威：发牌、形状提示、求援、求解 |
| `android/` | APK 工程：清单、MainActivity、图标、构建脚本 |
| `src/Sweeper.java` | Swing 桌面版，单文件无依赖，已与 JS 引擎同一口径 |
| `src/saolei.py`、`src/ui_sweeper.py`、`src/sweeper_core.py` | 早期 Python 实现，未同步，见下方「已知限制」 |
| `test/` | 回归与性能脚本，见下方「测试」 |

> 这份文档原先写的 `sweeper_app.html` 和 `hint_research.py` 在仓库里都不存在
> （单文件应用实际在 `android/assets/sweeper.html`）。已按实际路径改正。

## 引擎与内联副本

单文件是这个应用的设计约束（APK 里只装一个 html），所以引擎必须内联；
于是「一份代码」变成「两份文本 + 一个生成器」：

```sh
node test/sync-inline.mjs          # 把 lib/core.js 生成进 html 的内联引擎段
node test/sync-inline.mjs --check  # 只比对，不一致时退出码 1
```

改引擎只改 `lib/core.js`，然后跑生成器。`test/check-html.mjs` 会在两边漂移时报错，
也会用 `vm.Script` 解析两段脚本，确认单文件至少还能跑起来。

## 构建 APK

```sh
cd android
./build.sh
adb install -r build/sweeper.apk
```

依赖：`aapt2`（系统包）、`d8.jar` 与 `apksigner.jar`（`SDK=/opt/android-sdk`）、JDK。
签名用 `android/debug.keystore`（固定，勿删），否则升级会报签名冲突。

## 提示语义

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

「共有」只在形状覆盖该格全部未开邻格时给出（此时形状内雷数等于数字的剩余雷数），
所以两种提示都恒成立，不会随局面变化而失效。

形状内雷数的可证明区间是 `[剩余雷数 - 形状外未开邻格数, 剩余雷数]`：

- 下界够到形状格数 → 形状内全是雷（`hintEffect` 用它推进求解）；
- 剩余雷数为 0 → 形状内全安全；
- 其余情况只是「至少 n 颗」，**不构成任何安全结论**。
  早期版本把「下界为 0」当成「全安全」，等于在求解器里朝雷上开，
  高级档发牌因此反复空转（详见 `test/diag-candidates.mjs` 的输出）。

## 档位

```text
精简  只显示非平凡的精确值（0 < n < 形状格数）
标准  再加「至少 2 颗」的弱提示
全部  含平凡结论（全安全 / 全雷）
关闭  不显示提示，纯经典玩法
```

## 操作

触屏：

```text
点击        翻开
长按 0.4s   插旗 / 取消
拖 10px 以上 平移画面（放大后才有余量）
双指捏合     缩放 0.7x ~ 2.2x
点数字格     高亮该格提示覆盖的形状
```

桌面鼠标：

```text
左键        翻开
右键        插旗 / 取消
中键        排查（数字周围旗数够了就一次开周围）
滚轮/双指   缩放
```

两个按钮：

```text
「下一步」   求援：给出一格可确定的格，卡住时自动重算一次提示
「刷新提示」 整表重算（符号会变，仅在你主动点时发生）
```

## 测试

全部是本地脚本，无第三方依赖：

```sh
node test/check-html.mjs      # 两段脚本能否解析 + 内联引擎是否与 core.js 一致
node test/ui-smoke.mjs        # 用最小 DOM 桩真跑 openAt / chordAt / undo / findMove
node test/golden.mjs          # 输出行为基线（与 test/golden.json 对比可查回归）
node test/bench.mjs           # 发牌与中局重算的绝对耗时
node test/deal-stats.mjs 200  # 发牌成功率与耗时分布（p50 / p90 / p99）
node test/build-rate.mjs      # 单次 build 的成功率，决定 deal 的重试次数
node test/compare-orig.mjs    # 与仓库原始引擎（test/core-orig.mjs）逐项对比
```

Java 桌面版（JDK 17+，产物进 `build-java/`，已列进 `.gitignore`）：

```sh
javac -encoding UTF-8 -d build-java src/Sweeper.java
javac -encoding UTF-8 -cp build-java -d build-java test/java/EngineProbe.java
java -cp build-java EngineProbe 30      # 发牌成功率/耗时、提示自证、中局重算耗时
java -cp build-java IncrementalProbe    # 增量补算出的提示在推进中是否仍然恒成立
java -cp build-java ExactDump           # 打印一条「精确值不成立」提示的中间量（诊断用）
java -cp build-java Sweeper shot out.png  # 无头截图，顺带确认界面能起来
```

## 已知限制

- 新翻开的格子只做增量补算（新开格 + 邻居），整表重算只发生在「新局」和「刷新提示」。
- 形状里所有格子都被翻开后，该条提示不再绘制（没有可标的对象）。
- 形状提示由「形状内雷数落在 `[下界, 剩余雷数]`」这一条推出，不做多约束联合搜索：
  发牌时若一条提示推进不了局面，会继续加提示，连续 3 轮无进展就换一局
  （`build` 的 stall 判定）。高级档单次成功率约 2%，`deal` 靠重试兜到 200 局零失败。
- `src/saolei.py` / `src/ui_sweeper.py` / `src/sweeper_core.py` 是早期 Python 实现，
  提示逻辑仍包含旧的上界推断，**可能与网页版结论不同**，仅作算法参考，不再同步。
- `src/Sweeper.java` 已按同一口径修正（上下界与严格推断）并有独立探测，
  但它是 Swing 单文件，不参与 `test/sync-inline.mjs` 的生成，两边靠各自的测试守护。
