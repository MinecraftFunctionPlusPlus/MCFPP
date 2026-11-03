#math.list:_key
# 转动列表
# 查找键key的值是输入的位置
# 如果没查到则<res,int>输出0

execute store result score sloop int store result score stempn int if data storage math:io list[]

scoreboard players set res int 1
execute if score sloop int matches 1.. run function math.list:key_sloop
scoreboard players operation list_phi int %= stempn int
scoreboard players add res int 1
scoreboard players operation res int %= 2 int