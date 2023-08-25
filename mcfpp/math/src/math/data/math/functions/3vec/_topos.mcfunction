#math:3vec/_topos
# 转换函数：将3vec转换为执行者坐标
# 输入：3vec{<3vec_x,int>,<3vec_y,int>,<3vec_z,int>}
# 输出：执行者坐标

scoreboard players operation stempx int = x int
scoreboard players operation stempy int = y int
scoreboard players operation stempz int = z int
execute store result entity @s Pos[0] double 0.0001 run scoreboard players operation stempx int += 3vec_x int
execute store result entity @s Pos[1] double 0.0001 run scoreboard players operation stempy int += 3vec_y int
execute store result entity @s Pos[2] double 0.0001 run scoreboard players operation stempz int += 3vec_z int