#math:set/subset_sloop
data modify entity @s Tags append from storage math:io stemp_tags[-1]
execute store result score sstemp1 int if data entity @s Tags[]
execute if score sstemp1 int > sstemp0 int store success score sloop int run scoreboard players set res int 0
#循环迭代
data remove storage math:io stemp_tags[-1]
scoreboard players remove sloop int 1
execute if score sloop int matches 1.. run function math:set/subset_sloop