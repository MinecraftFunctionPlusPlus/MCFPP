#math:3vec/_coordto
# 转换函数：将执行坐标转换为3vec
# 需要传入世界实体为执行者
# 输入：执行坐标
# 输出：3vec{<3vec_x,int>,<3vec_y,int>,<3vec_z,int>}

tp @s ~ ~ ~
execute store result score 3vec_x int run data get entity @s Pos[0] 10000
execute store result score 3vec_y int run data get entity @s Pos[1] 10000
execute store result score 3vec_z int run data get entity @s Pos[2] 10000