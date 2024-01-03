#> mcfpp.dynamic:data/modify/storage.append.from.entity_sp
# 特别用于mcfpp编译。当有s[a.p]时，只有p为一个动态nbt，且为类成员的时候调用
# @api
# @input path

$data modify storage mcfpp:arg path_join.pathList append from entity @s $(targetPath)