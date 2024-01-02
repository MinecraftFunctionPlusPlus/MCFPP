#> mcfpp.dynamic:data/modify/storage.from.entity_sp3
# 特别用于mcfpp编译。当nbt a = b时，若只有b是类成员的时候调用此函数。
# @api
# @input (source=mcfpp:system) sourcePath (target=@s) targetPath

$data modify storage mcfpp:system $(sourcePath) set from entity @s data.$(targetPath)