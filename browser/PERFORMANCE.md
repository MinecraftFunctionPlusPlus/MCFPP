# TeaVM Playground 验收记录

2026-11-07，本机 Windows、现有 Edge 浏览器会话及本地 VitePress 服务
`http://127.0.0.1:10428/zh/playground.html`。TeaVM 0.16.0 / WasmGC，构建与 JVM 对照使用 JDK 25。
计时在 Gradle 构建结束后进行。浏览器每轮创建新 Worker、首次点击才准备标准库，没有预热编译。
浏览器会话及 HTTP 缓存复用；这里的初始化时间不能代表公网首次下载时间。

## 实测

| 场景 | 浏览器编译 | JVM 编译 | 浏览器点击至 DSB 完成 |
| --- | ---: | ---: | ---: |
| 首次：两个函数、print、list.add/size | 2.021 s | 3.035 s | 2.585 s |
| 相同源码复编 | 0.148 s | 0.212 s | 0.677 s |
| 修改源码、再次 add | 0.156 s | 0.262 s | 0.722 s |
| 语法错误 | 0.032 s | 0.085 s | 0.056 s |
| 修复后重编译 | 0.124 s | 0.364 s | 0.669 s |
| 继承标准库 CommandResult | 0.677 s | 0.484 s | 2.466 s |
| 继承示例复编 | 0.626 s | 0.647 s | 2.272 s |
| 自定义 MNI 声明被拒绝 | 0.037 s | 0.061 s | 0.064 s |
| 外部列表、字符串、整数和布尔输出 | 0.751 s | 0.680 s | 2.012 s |
| 用户定义同名 print | 0.167 s | 0.276 s | 0.706 s |
| 恢复默认示例 | 0.131 s | 0.252 s | 0.637 s |

页面显示初始化 **0.66 s**；自动化从刷新至编译按钮可用测得 **0.962 s**。
首次编译内核计时为 1.994 s，其中标准库/基线准备 1.358 s、编译 0.586 s、恢复 0.045 s。
表中浏览器编译计时还包含 Worker 消息往返；JVM 计时不包含进程启动。
本次验收的首次编译和所有复编均低于用户要求的 10 秒。

## 默认示例的产物优化

此前 O2 虽已开启，基础 `print` 和列表长度查询却让函数回退到旧后端。
2026-11-06 的验收产物中，`main.mcfunction` 仍有 8 条命令，包括栈帧操作和两次列表赋值。
现已将基础输出和长度查询纳入 typed IR，内联 `greet`，折叠长度并删除无人观察的列表和栈帧。
当前浏览器和 JVM 都生成下面两条命令；公开的 `greet.mcfunction` 仍可单独选择、运行。

```mcfunction
tellraw @a {"type": "text", "text": "Hello, MCFPP!"}
tellraw @a {"type": "text", "text": "3"}
```

## 核对结果与已知限制

- 11 组同源输入的生成文件路径和内容与 JVM **逐字节一致**。
- 文件切换可展示 greet/main；DSB 输出 `Hello, MCFPP!` 和 `3`，修改后为 `4`。
- 语法错误和被禁用的 MNI 声明返回实际诊断、红色提示、零产物；修复后可继续编译。
- 继承示例及其复编均在 DSB 输出两个 `0`；后续默认示例没有旧文件或诊断残留。
- 编译器网络请求只有本地元数据、加载器、Wasm 的 GET；没有源码 POST、JAR 或 CheerpJ 请求。
- 浏览器控制台无错误；已查看成功与语法错误页面截图。
- 54 项现有及新增检查、Wasm 构建和 VitePress 生产构建通过。
- 用户定义的同名 `print` 保持普通函数语义，DSB 输出 `12`。
- **DSB 0.2.2 已知失败**：外部列表经原生命令追加到 `[10,20,30]` 后，
  `execute store result score ... run data get storage ...` 在 DSB 中返回 `1`，预期为长度 `3`。
  该用例的后续输出 `0 / changed / 2 / 1` 正确。验收脚本将此项写入 `limitations` 并打印
  `KNOWN FAILURE`，没有把动态长度执行验证计为通过；本轮未用 Minecraft 客户端验证此例。
  默认示例的常量长度折叠及 DSB 结果已通过。
- 原分支完整桌面、多版本和外部扩展回归不属于本次验收。

Wasm：41,645,764 字节，SHA-256：
`2faf4c138ed101133d87ec0d8768ea1e1053c390d06518e1f2c821ea5d5d4f58`。
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

本地原始证据：MCFPP 的 `output/default-optimization-tests.log`、`output/default-optimization-final-build.log`、
`browser/build/reports/browser/native-results.json`；MCFPPAPI 的
`output/playwright/default-optimization-browser.log`、`output/playwright/teavm-browser-results.json`、
`output/playwright/teavm-success.png`、`output/playwright/teavm-error.png`、`output/default-optimization-docs-build.log`。
这些是忽略的构建/验收产物，重建时重新生成。固定输入保存在 `benchmarks/cases.json`。

旧 CheerpJ 记录的首次编译约 45.19 s，属于之前版本的历史证据；本轮没有重新执行同版本 A/B。
