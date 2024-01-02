#> mcfpp.dynamic.util:nbt/join_path
# @api
# @input storage mcfpp:arg path_join.pathList
# @input storage mcfpp:arg path_join.base
# @output storage mcfpp:arg path_join.base

execute store result score length mcfpp_temp run data get storage mcfpp:arg pathList

execute if score length mcfpp_temp matches 0 run return 1
data modify storage mcfpp:arg path_join.append set from storage mcfpp:arg pathList[0]
function mcfpp.dynamic.util:nbt/z_join with storage mcfpp:arg path_join
data remove storage mcfpp:arg path_join.pathList[0]
function mcfpp.dynamic.util:nbt/join_path