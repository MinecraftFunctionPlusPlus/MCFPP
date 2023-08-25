#math:_posto
# 坐标转换函数
# 输入执行者坐标
# 输出{<x,int>,<y,int>,<z,int>}
execute store result score x int run data get entity @s Pos[0] 10000
execute store result score y int run data get entity @s Pos[1] 10000
execute store result score z int run data get entity @s Pos[2] 10000