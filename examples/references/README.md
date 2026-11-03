# 显式地址引用示例

此项目使用随编译器提供的标准库，目标版本为 26.3。在仓库根目录编译：

```powershell
./gradlew run --args="build --project examples/references/mcfpp.toml"
```

将输出的 `build/mcfpp/` 和 `build/Imports/` 数据包一起加载后调用：

```mcfunction
function example.references:main
```

`main` 为假玩家建立零分，通过普通函数参数传递 `ScoreRef` 并增加到 1，输出 `shared-score`。随后复制一个 `NBTRef<int>`、重赋原引用、通过副本写入原 storage 地址，输出 `saved-storage`。

末尾先从 storage 读取路径字符串，再用该运行期字符串创建引用，经普通函数返回、列表保存和取出后写入 21。同一个 `observe` 函数先读取成功，再在目标字段删除后读取失败：成功时输出一次 `runtime-value`，失败时跳过读取正文并输出 `runtime-missing`。第二次调用不会把第一次的值交给回调。

正常 CLI 导出与执行验收已核对输出目录、入口名及以下结果：消息顺序为 `shared-score`、`saved-storage`、`runtime-value`、`runtime-missing`。最终 `#example reference_points = 1`；`example:references` 保留 `{first:10,second:99,selectedPath:"runtimeValue"}`，`runtimeValue` 字段已删除。

以实体为执行者调用 `example.references:inspect` 可观察实体 `Air` 是否存在；成功时输出 `air-present`，缺失时输出 `air-missing`。

引用类型、地址工厂与读取合同见[使用说明](../../docs/type-system/reference-values.md)。
