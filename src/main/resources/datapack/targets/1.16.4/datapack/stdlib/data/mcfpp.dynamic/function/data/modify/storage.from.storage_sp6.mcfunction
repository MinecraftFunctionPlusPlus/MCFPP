#> mcfpp.dynamic:data/modify/storage.from.storage_sp4
# @api
# @input (source=mcfpp:temp) sourcePath (target=mcfpp:system) targetPath

$data modify storage mcfpp:temp temp.$(sourcePath) set from storage mcfpp:system $(targetPath)