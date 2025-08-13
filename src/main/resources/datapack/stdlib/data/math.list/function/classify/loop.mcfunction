#math.list:classify/loop
scoreboard players set sstemp int 1
#进入转盘
scoreboard players operation ssloop int = classify_cnt int
execute if score ssloop int matches 1.. run function math.list:classify/sloop
#添加进分类
execute if score sstemp int matches 1 run function math.list:classify/new
data modify storage math:io classify[-1] append from storage math:io stemp[0]
#循环
data remove storage math:io stemp[0]
scoreboard players remove sloop int 1
execute if score sloop int matches 1.. run function math.list:classify/loop