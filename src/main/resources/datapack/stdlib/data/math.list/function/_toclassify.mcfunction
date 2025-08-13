#math.list:_toclassify
#输出置空
data modify storage math:io classify set value []
scoreboard players set classify_cnt int 0
#循环遍历列表
data modify storage math:io stemp set from storage math:io list
execute store result score sloop int if data storage math:io list[]
execute if score sloop int matches 1.. run function math.list:classify/loop