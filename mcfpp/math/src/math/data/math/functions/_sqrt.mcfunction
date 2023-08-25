#math:_sqrt
# 计算整数精度开根号
# 输入<inp,int>
# 输出<res,int>
execute store result score stemp0 int store result score stemp1 int store result score stemp2 int store result score stemp3 int run scoreboard players operation res int = inp int
execute if score inp int matches ..13924 run function math:sqrt/range0
execute if score inp int matches 13925..16777216 run function math:sqrt/range1
execute if score inp int matches 16777217.. run function math:sqrt/range2
scoreboard players operation stemp0 int /= res int
scoreboard players operation res int += stemp0 int
scoreboard players operation res int /= 2 int
scoreboard players operation stemp1 int /= res int
scoreboard players operation res int += stemp1 int
scoreboard players operation res int /= 2 int
scoreboard players operation stemp2 int /= res int
scoreboard players operation res int += stemp2 int
scoreboard players operation res int /= 2 int
scoreboard players operation stemp3 int /= res int
scoreboard players operation res int += stemp3 int
scoreboard players operation res int /= 2 int