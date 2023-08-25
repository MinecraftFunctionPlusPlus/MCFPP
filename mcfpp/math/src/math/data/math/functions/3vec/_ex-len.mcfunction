#math:3vec/_ex-rot
# 扩展函数：对3vec进行长度属性扩展
# 输入：3vec{<3vec_x,int>,<3vec_y,int>,<3vec_z,int>}
# 输出：3vec{<3vec_x,int>,<3vec_y,int>,<3vec_z,int>,<3vec_l,int>}

scoreboard players operation stempx int = 3vec_x int
scoreboard players operation stempy int = 3vec_y int
scoreboard players operation stempz int = 3vec_z int

scoreboard players set stemp4 int 0
execute unless score 3vec_x int matches -26754..26754 run scoreboard players set stemp4 int 1
execute unless score 3vec_y int matches -26754..26754 run scoreboard players set stemp4 int 1
execute unless score 3vec_z int matches -26754..26754 run scoreboard players set stemp4 int 1
execute if score stemp4 int matches 1 run function math:3vec/exlen/3div

scoreboard players operation stempx int *= stempx int
scoreboard players operation stempy int *= stempy int
scoreboard players operation stempz int *= stempz int
scoreboard players operation stempx int += stempy int
scoreboard players operation stempx int += stempz int

execute store result score stemp0 int store result score stemp1 int store result score stemp2 int store result score stemp3 int run scoreboard players operation 3vec_l int = stempx int
execute if score stempx int matches ..13924 run function math:3vec/exlen/range0
execute if score stempx int matches 13925..16777216 run function math:3vec/exlen/range1
execute if score stempx int matches 16777217.. run function math:3vec/exlen/range2
scoreboard players operation stemp0 int /= 3vec_l int
scoreboard players operation 3vec_l int += stemp0 int
scoreboard players operation 3vec_l int /= 2 int
scoreboard players operation stemp1 int /= 3vec_l int
scoreboard players operation 3vec_l int += stemp1 int
scoreboard players operation 3vec_l int /= 2 int
scoreboard players operation stemp2 int /= 3vec_l int
scoreboard players operation 3vec_l int += stemp2 int
scoreboard players operation 3vec_l int /= 2 int
scoreboard players operation stemp3 int /= 3vec_l int
scoreboard players operation 3vec_l int += stemp3 int
scoreboard players operation 3vec_l int /= 2 int

execute if score stemp4 int matches 1 run scoreboard players operation 3vec_l int *= 10 int