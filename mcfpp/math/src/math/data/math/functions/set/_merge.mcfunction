#math:set/_merge
# 求并集
# 输入临时对象,执行者对象
# 输出临时对象

data modify storage math:io stemp_Tags set from entity @s Tags
data modify entity @s Tags set from storage math:io set_tags

data modify storage math:io stemp set from entity @s data.set
data modify storage math:io stemp_tags set from entity @s data.set_tags
scoreboard players operation sloop int = @s set_cnt
execute if score sloop int matches 1.. run function math:set/merge_sloop

data modify entity @s Tags set from storage math:io stemp_Tags