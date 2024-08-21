#math:obj_entity/_reid
#重置对象计数器(谨慎使用！！！)
#确保未加载区块无实体对象
#确保无非实体组实体有int定义
#会重新分配id和int指针
execute as @e if score @s id matches ..2147483647 run tag @s add tmp
scoreboard objectives add reid_temp dummy
execute as @e[tag=tmp] run scoreboard players operation @s reid_temp = @s id
scoreboard players set #id id -1
execute as @e[tag=tmp] store result score @s id run scoreboard players add #id id 1
execute as @e if score @s int matches ..2147483647 run function math:obj_entity/get_newint
scoreboard objectives remove reid_temp
tag @e remove tmp