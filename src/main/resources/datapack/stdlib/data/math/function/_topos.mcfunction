#math:_topos
# 坐标转换函数
# 输入{<x,int>,<y,int>,<z,int>}
# 输出执行者坐标
execute store result entity @s Pos[0] double 0.0001 run scoreboard players get x int
execute store result entity @s Pos[1] double 0.0001 run scoreboard players get y int
execute store result entity @s Pos[2] double 0.0001 run scoreboard players get z int