#math.list:key_sloop
data modify storage math:io list append from storage math:io list[0]
data remove storage math:io list[0]
scoreboard players add list_phi int 1

data modify storage math:io stemp_cmp set from storage math:io list[0].key
execute store success score res int run data modify storage math:io stemp_cmp set from storage math:io input
execute if score res int matches 0 run scoreboard players set sloop int 1
#循环迭代
scoreboard players remove sloop int 1
execute if score sloop int matches 1.. run function math.list:key_sloop