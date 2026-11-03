#> mcfpp.dynamic:data/modify/storage.from.entity_sp3
# 特别用于mcfpp编译。当nbt a = b时，若只有b是类成员的时候，且a是临时变量的时候调用此函数。
# @api
# @input (source=mcfpp:temp) sourcePath (target=@s) targetPath

$data modify storage mcfpp:temp temp.$(sourcePath) set from entity @s data.$(targetPath)