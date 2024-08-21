#math:obj_entity/set
execute store result score @s id run scoreboard players add #id id 1
data modify entity @s Tags set value ["obj_entity"]
data modify storage math:io result set from entity @s UUID