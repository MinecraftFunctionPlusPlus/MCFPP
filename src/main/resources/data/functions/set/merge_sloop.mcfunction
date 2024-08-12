#math:set/merge_sloop
data modify entity @s Tags append from storage math:io stemp_tags[-1]
execute store result score sstemp int if data entity @s Tags[]
execute if score sstemp int > set_cnt int run function math:set/merge_success
#循环迭代
data remove storage math:io stemp[-1]
data remove storage math:io stemp_tags[-1]
scoreboard players remove sloop int 1
execute if score sloop int matches 1.. run function math:set/merge_sloop