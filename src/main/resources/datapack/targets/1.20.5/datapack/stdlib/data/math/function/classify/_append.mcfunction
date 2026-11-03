#math:classify/_append
scoreboard players set sstemp int 1
#进入转盘
scoreboard players operation ssloop int = classify_cnt int
execute if score ssloop int matches 1.. run function math:classify/sloop
#添加进分类
execute if score sstemp int matches 1 run function math:classify/new_group
data modify storage math:io classify[-1] append from storage math:io input