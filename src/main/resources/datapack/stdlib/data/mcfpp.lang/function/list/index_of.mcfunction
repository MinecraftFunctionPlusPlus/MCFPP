# @param list 要检查的列表
# @param element 要检查的元素

# list.index mcfpp_temp当前的索引
# list.size mcfpp_temp列表的长度

# 检查索引是否越界
execute if score list.index mcfpp_temp >= list.size mcfpp_temp run return -1
# 检查第一个元素
execute store success score #if_success mcfpp_temp run data modify storage mcfpp:system list.list[0] set from storage mcfpp:system list.element
# 如果元素符合
execute unless score #if_success mcfpp_temp matches 1 run return run scoreboard players get list.index mcfpp_temp
# 否则，检查第二个元素
data remove storage mcfpp:system list.list[0]
scoreboard players add list.index mcfpp_temp 1
function mcfpp.lang:list/index_of
return -1
