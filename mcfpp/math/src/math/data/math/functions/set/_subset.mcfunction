#math:set/_subset
# 判断执行者是不是临时对象的子集
# 输入实体对象，临时对象
# 输出到<res,int>

scoreboard players set res int 1

data modify storage math:io stemp_Tags set from entity @s Tags
data modify entity @s Tags set from storage math:io set_tags
scoreboard players operation sstemp0 int = set_cnt int

data modify storage math:io stemp_tags set from entity @s data.set_tags
scoreboard players operation sloop int = @s set_cnt
execute if score sloop int matches 1.. run function math:set/subset_sloop

data modify entity @s Tags set from storage math:io stemp_Tags