SignedMine 0.3.25 便携版（Windows x64）
=====================================

双击 SignedMine.exe 开始。


操作
----
左键        翻开
右键        插旗 / 取消
中键        排查（数字周围旗数够了就一次开周围）
双击数字    同上

提示
  数字格右上角  至少 n 颗   该形状里至少有 n 颗雷
  数字格右下角  共有 n 颗   该形状里恰好有 n 颗雷

  两种提示都恒成立，不会随局面变化而失效。工具栏可以切换显示档位（精简 / 标准 / 全部）
  或关掉提示；「提示」按钮会给出一格能确定的格。


不需要装 Java
-------------
运行时在 runtime\ 里，只包含 Swing 用到的模块（java.base、java.datatransfer、
java.xml、java.prefs、java.desktop），没有依赖系统里的 JDK 或 JRE。


网页版
------
web\sweeper.html 用浏览器打开即玩，不需要服务器。


卸载
----
整个目录删掉就行。不写注册表，不留别的文件。


源码与玩法细节
--------------
https://github.com/Nesarf/SignedMine
