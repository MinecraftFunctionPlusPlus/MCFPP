#> mcfpp.dynamic:data/modify/entity.from.storage_sp
# 特别用于mcfpp编译。当nbt a = b时，若ab都是类成员的时候调用此函数。
# @api
# @input (source=@s) sourcePath (target=mcfpp:temp) targetPath

$data modify entity @s data.$(sourcePath) set from storage mcfpp:temp temp.$(targetPath)