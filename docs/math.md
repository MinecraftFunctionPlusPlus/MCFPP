# 标量数学标准库

`import mcfpp.math:*` 后可使用：

- `abs`、`sign`、`min`、`max`、`clamp`、`square` 的 int/float 重载。
- `floor`、`ceil`、`trunc`、`round`：float 输入及输出。round 的半整数向正无穷取整。
- `sqrt(float)`、`isqrt(int)`、`cbrt(float)`、`nroot(float,int)`。
- `pow(int,int)`、`pow(float,int)`；26.3 另提供 `pow(float,float)`。
- `sin`、`cos`、`tan`、`asin`、`acos`、`atan`、`atan2(y,x)`：角度均使用弧度。
- `sinCos(float)` 返回具有 `sin`、`cos` 两个 float 字段的 `SinCos`。
- `toRadians`、`toDegrees`、两参数和三参数 `hypot`、`lerp(a,b,t)`。
- `floorDiv(int,int)`、`floorMod(int,int)`：负数使用向负无穷取整的除法。

`isqrt` 返回精确的向下取整整数平方根。即使目标支持数值提供器，也会对浮点初值进行整数校正。
`lerp` 按 `a+(b-a)*t` 求值，允许外插。`clamp` 要求下限不大于上限。
`sqrt/isqrt` 要求非负输入；`nroot` 要求 n >= 2，负输入要求奇数次根。
`pow(int,int)` 要求非负指数；`pow(float,int)` 支持负指数。0 的 0 次幂未定义。
`asin/acos` 的输入范围为 [-1,1]。
已知的非法原生操作输入产生编译诊断。原生计算结果先初始化为零，运行时计算失败时不会读取上一次调用的结果。
整数幂溢出返回零；普通整数运算符仍采用原有语言语义。

26.3 根据 `TargetCapabilities` 使用单精度数值提供器。旧目标使用八位十进制有效数字的四分量数学实现。
数学原生函数保留运行时计算，避免以 JVM Math 的结果替代旧库算法或原版三角函数的舍入。
即使实参是常量，原生数学函数也生成运行时计算；这些调用不能用于要求编译期值的 const 初始化。
反三角运算使用 math3.1 的实体朝向算法；每次调用创建并销毁计算实体。
atan2 的两个输入先按共同尺度归一化到万分之一定点值，因此非常小的分量会量化为零。
旧目标的正余弦也使用这个定点精度。各后端精度不同，结果不保证逐位相同。

数值库仅初始化记分板及常量。正余弦等原生运算不引入旧 math 资源；
旧浮点运算与反三角函数通过现有链接器按实际依赖保留资源。
启动代码单独记录数学库数值版本，因此已经初始化旧库的世界在重新加载数据包后也会补齐新增常量。

上游来源为 [math3.1 3.1.5](https://github.com/xiaodou8593/math3.1/tree/187aa724c8df9f16393b9f8b1c8bbe712378e36c)，
提交 `187aa724c8df9f16393b9f8b1c8bbe712378e36c`。
更新脚本仅合入平方根、正余弦、方位角及其函数依赖；保留 MCFPP 已有加减、乘除、整数转换补丁。
上游 `math:float/*` 映射到现有 `math.float:hpo/float/*`，维持后端调用 ABI。
取整、整数幂、精确整数平方根和 n 次方根的适配器由更新脚本生成。
n 次方根沿相邻十进制幂建立区间后进行二分，避免上游固定次数 Newton 迭代在高次根上的收敛问题。

```text
python tools/update_math.py --source /path/to/math3.1
./gradlew rebuildStdlib
```

可以使用 `-PstdlibTargets=26.3,1.21.8` 重建指定目标。
重建会同步声明索引、语义函数体及所引用的资源；随后正常构建会重新生成 Playground 的导入索引。
