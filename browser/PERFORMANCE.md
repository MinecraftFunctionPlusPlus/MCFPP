# TeaVM Playground 验收记录

2026-11-06，本机 Windows、现有浏览器会话及本地 VitePress 服务
`http://127.0.0.1:10428/zh/playground.html`。TeaVM 0.16.0 / WasmGC，构建与 JVM 对照使用 JDK 25。
计时在 Gradle 构建结束后进行。浏览器每轮创建新 Worker、首次点击才准备标准库，没有预热编译。
浏览器会话及 HTTP 缓存复用；这里的初始化时间不能代表公网首次下载时间。

## 实测

| 场景 | 浏览器编译 | JVM 编译 | 浏览器点击至 DSB 完成 |
| --- | ---: | ---: | ---: |
| 首次：两个函数、print、list.add/size | 2.114 s | 3.093 s | 2.866 s |
| 相同源码复编 | 0.168 s | 0.203 s | 0.878 s |
| 修改源码、再次 add | 0.177 s | 0.287 s | 0.980 s |
| 语法错误 | 0.046 s | 0.067 s | 0.072 s |
| 修复后重编译 | 0.141 s | 0.348 s | 0.823 s |
| 继承标准库 CommandResult | 0.599 s | 0.316 s | 2.129 s |
| 继承示例复编 | 0.574 s | 0.445 s | 2.076 s |
| 自定义 MNI 声明被拒绝 | 0.044 s | 0.054 s | 0.072 s |
| 恢复默认示例 | 0.173 s | 0.275 s | 0.875 s |

页面显示初始化 **0.58 s**；自动化从刷新至编译按钮可用测得 **0.917 s**。
首次编译内核计时为 2.089 s，其中标准库/基线准备 1.500 s、编译 0.545 s、恢复 0.040 s。
表中浏览器编译计时还包含 Worker 消息往返；JVM 计时不包含进程启动。
本次验收的首次编译和所有复编均低于用户要求的 10 秒。

## 核对结果

- 9 组同源输入的生成文件路径和内容与 JVM **逐字节一致**。
- 文件切换可展示 greet/main；DSB 输出 `Hello, MCFPP!` 和 `3`，修改后为 `4`。
- 语法错误和被禁用的 MNI 声明返回实际诊断、红色提示、零产物；修复后可继续编译。
- 继承示例及其复编均在 DSB 输出两个 `0`；后续默认示例没有旧文件或诊断残留。
- 编译器网络请求只有本地元数据、加载器、Wasm 的 GET；没有源码 POST、JAR 或 CheerpJ 请求。
- 浏览器控制台无错误；已查看成功与语法错误页面截图。
- 核心协议/算法检查和 Wasm 构建通过。原分支完整桌面、多版本和外部扩展回归不属于本次验收。

Wasm：41,611,749 字节，SHA-256：
`bf64b4127e6678caf25de364efc7a7886e01c7c61fe707429c58584b34e2c98d`。
网站的 `teavm.json` 同时记录配套加载器及调试文件哈希；生成资源通过 Git 属性保持原始字节。

## 命令与证据

```powershell
# MCFPP
.\gradlew.bat test :browser:buildWasmGC
.\gradlew.bat :browser:benchmarkKernel

# MCFPPAPI；使用已打开的文档站浏览器会话
pnpm playground:prepare
node scripts/benchmark-playground.mjs
npx --yes --package @playwright/cli playwright-cli -s=mcfpp-perf-fix run-code --filename output/playwright/teavm-acceptance.cjs
# 将上一命令完整输出保存后执行：
node scripts/benchmark-playground.mjs --compare output/playwright/teavm-browser.log
pnpm playground:typecheck
pnpm docs:build
```

本地原始证据：MCFPP 的 `output/teavm-final-check.log`、`output/teavm-native-final.log`、
`browser/build/reports/browser/native-results.json`；MCFPPAPI 的
`output/playwright/teavm-browser.log`、`teavm-browser-results.json`、`teavm-success.png`、`teavm-error.png`。
这些是忽略的构建/验收产物，重建时重新生成。固定输入保存在 `benchmarks/cases.json`。

旧 CheerpJ 记录的首次编译约 45.19 s，属于之前版本的历史证据；本轮没有重新执行同版本 A/B。
