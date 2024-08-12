#math:3vec/_posto
# 转换函数：将执行者坐标转换为3vec
# 输入：执行者坐标
# 输出：3vec{<3vec_x,int>,<3vec_y,int>,<3vec_z,int>}

execute store result score 3vec_x int run data get entity @s Pos[0] 10000
execute store result score 3vec_y int run data get entity @s Pos[1] 10000
execute store result score 3vec_z int run data get entity @s Pos[2] 10000
scoreboard players operation 3vec_x int -= x int
scoreboard players operation 3vec_y int -= y int
scoreboard players operation 3vec_z int -= z int