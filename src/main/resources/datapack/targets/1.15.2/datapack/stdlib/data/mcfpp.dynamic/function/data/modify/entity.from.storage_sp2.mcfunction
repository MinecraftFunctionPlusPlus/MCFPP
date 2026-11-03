#> mcfpp.dynamic:data/modify/entity.from.storage_sp2
# 特别用于mcfpp编译。当nbt a = b时，若只有a是类成员的时候调用此函数。
# @api
# @input (source=@s) sourcePath (target=mcfpp:system) targetPath

$data modify entity @s $(sourcePath) set from storage mcfpp:system $(targetPath)