#math.list:_toset
# 转换函数:列表->集合
# 需要传入世界实体为执行者

data modify entity @s Tags set value []

data modify storage math:io set set value []
data modify storage math:io set_tags set value []
scoreboard players set set_cnt int 0

data modify storage math:io stemp set from storage math:io list
execute store result score sloop int if data storage math:io stemp[]
execute if score sloop int matches 1.. run function math.list:set/sloop
data modify storage math:io set_tags set from entity @s Tags

data modify entity @s Tags set value ["math_marker"]