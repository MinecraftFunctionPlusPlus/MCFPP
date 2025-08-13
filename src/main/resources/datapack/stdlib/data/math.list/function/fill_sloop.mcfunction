#math.list:fill_sloop
data modify storage math:io list append from storage math:io input
#循环迭代
scoreboard players remove sloop int 1
execute if score sloop int matches 1.. run function math.list:fill_sloop