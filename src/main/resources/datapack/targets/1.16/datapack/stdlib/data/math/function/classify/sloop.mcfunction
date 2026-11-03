#math:classify/sloop
#比较元素，如果相同，转盘停止
data modify storage math:io stemp_cmp set from storage math:io classify[0][0].key
execute store success score sstemp int run data modify storage math:io stemp_cmp set from storage math:io input.key
execute if score sstemp int matches 0 run scoreboard players set ssloop int 1
#循环
data modify storage math:io classify append from storage math:io classify[0]
data remove storage math:io classify[0]
scoreboard players remove ssloop int 1
execute if score ssloop int matches 1.. run function math:classify/sloop