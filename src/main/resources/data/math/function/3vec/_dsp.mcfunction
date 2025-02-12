#math:3vec/_dsp
# 对3vec进行粒子显示
# 需要传入世界实体为执行者
# 输入：<particle,int>,3vec{<3vec_x,int>,<3vec_y,int>,<3vec_z,int>,<3vec_n,int>,<3vec_l,int>}

execute store result entity @s Pos[0] double 0.0001 run scoreboard players operation stemp0 int = 3vec_x int
execute store result entity @s Pos[1] double 0.0001 run scoreboard players operation stemp1 int = 3vec_y int
execute store result entity @s Pos[2] double 0.0001 run scoreboard players operation stemp2 int = 3vec_z int
scoreboard players operation stemp3 int = stemp0 int
scoreboard players operation stemp4 int = stemp1 int
scoreboard players operation stemp5 int = stemp2 int
scoreboard players operation stemp3 int %= 3vec_n int
scoreboard players operation stemp4 int %= 3vec_n int
scoreboard players operation stemp5 int %= 3vec_n int
scoreboard players operation stemp0 int /= 3vec_n int
scoreboard players operation stemp1 int /= 3vec_n int
scoreboard players operation stemp2 int /= 3vec_n int
execute positioned 0.0 0.0 0.0 facing entity @s feet run tp @s ~ ~ ~ ~ ~

tp @s ~ ~ ~
execute store result score sstemp0 int run data get entity @s Pos[0] 10000
execute store result score sstemp1 int run data get entity @s Pos[1] 10000
execute store result score sstemp2 int run data get entity @s Pos[2] 10000

scoreboard players set sstemp3 int 0
scoreboard players set sstemp4 int 0
scoreboard players set sstemp5 int 0
scoreboard players set sloop int 0
execute if score sloop int < 3vec_n int run function math:3vec/dsp/loop0

execute if entity @p at @s run function math:3vec/dsp/arrow