#math:set/_diff
# 求差集
# 用实体对象减去临时对象
# 输入临时对象,执行者对象
# 输出临时对象

data modify storage math:io stemp_Tags set from entity @s Tags
data modify entity @s Tags set from entity @s data.set_tags
scoreboard players operation sstemp0 int = @s set_cnt

data modify storage math:io stemp set from storage math:io set
data modify storage math:io stemp_tags set from storage math:io set_tags
scoreboard players operation sloop int = set_cnt int
data modify storage math:io set set value []
data modify storage math:io set_tags set value []
scoreboard players set set_cnt int 0
execute if score sloop int matches 1.. run function math:set/diff_sloop

data modify entity @s Tags set from storage math:io stemp_Tags