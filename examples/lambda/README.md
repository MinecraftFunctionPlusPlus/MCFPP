# 同步 lambda 示例

从项目根目录编译和运行。语法与限制见 [教程](../../docs/lambda/tutorial.md)，类型标注为冒号，函数类型写 `(int) -> int`。

| 源码 | 行为 | 对应生成检查 |
| --- | --- | --- |
| [basic/main.mcfpp](basic/main.mcfpp) | 加法、重复调用、函数引用，结果 `5 / 9 / 7` | `LambdaCallTest` |
| [basic/control.mcfpp](basic/control.mcfpp) | 参数求值一次、尾表达式、局部返回和 `void` 副作用 | `LambdaCallTest` |
| [capture/main.mcfpp](capture/main.mcfpp) | 局部快照、全局、嵌套/循环实例、调用点上下文 | `LambdaCaptureTest` |
| [escape/main.mcfpp](escape/main.mcfpp) | 返回闭包、复制、两个工厂实例与嵌套返回 | `LambdaEscapeTest` |
| [dynamic/main.mcfpp](dynamic/main.mcfpp) | 运行时分支、环境与接收者、实参求值次数 | `LambdaDynamicTest` |
| [reentry/main.mcfpp](reentry/main.mcfpp) | 同步重入后外层继续计算 | `LambdaReentryTest` |
| [collections/main.mcfpp](collections/main.mcfpp) | 真实列表 `map/filter/fold/foreach`，完整结果及原列表 | `LambdaHigherOrderTest` |

这些测试生成 `build/lambda/sync-*` 数据包和 `.dps.json` 清单；库往返检查生成 `build/lambda/library/{source,imported}`。编译成功后还应实际运行清单，检查观察结果和返回栈。具体本工作区运行命令见教程。

引用捕获及 lambda/MNI `REF` 互操作暂缓；不要把这些示例改写成捕获外层局部后原地累加。使用回调返回值或 `fold` 显式累积。
