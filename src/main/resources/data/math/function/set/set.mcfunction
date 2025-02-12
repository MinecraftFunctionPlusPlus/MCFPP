#math:set/set
execute store result score @s id run scoreboard players add #id id 1
scoreboard players set @s set_cnt 0
data modify entity @s Tags append from storage math:io input.tags[]