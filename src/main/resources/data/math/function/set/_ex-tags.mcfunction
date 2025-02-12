#math:set/_ex-tags
# 扩展tags属性

data modify storage math:io set_tags set value []
data modify storage math:io stemp set from storage math:io set
scoreboard players operation sloop int = set_cnt int
execute if score sloop int matches 1.. run function math:set/tags_sloop