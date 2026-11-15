#math:_sqrt
# 整数精度开根号
# 输入<inp,int>
# 输出<res,int>

execute store result score stemp0 int store result score stemp1 int run scoreboard players operation res int = inp int
execute if score inp int matches 1..329475 run function math:sqrt/range_0
execute if score inp int matches 329476..18688328 run function math:sqrt/range_1
execute if score inp int matches 18688329..533609999 run function math:sqrt/range_2
execute if score inp int matches 533610000.. run function math:sqrt/range_3
scoreboard players operation stemp0 int /= res int
scoreboard players operation res int += stemp0 int
scoreboard players operation res int /= 2 int
scoreboard players operation stemp1 int /= res int
scoreboard players operation res int += stemp1 int
scoreboard players operation res int /= 2 int
