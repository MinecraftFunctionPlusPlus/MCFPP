#math.list:rot_loop1
#顺时针转动
data modify storage math:io list prepend from storage math:io list[-1]
data remove storage math:io list[-1]
#循环迭代
scoreboard players add stemp0 int 1
execute if score stemp0 int matches ..-1 run function math.list:rot_loop1