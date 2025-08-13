#math.list:rot_loop0
#逆时针转动
data modify storage math:io list append from storage math:io list[0]
data remove storage math:io list[0]
#循环迭代
scoreboard players remove stemp0 int 1
execute if score stemp0 int matches 1.. run function math.list:rot_loop0