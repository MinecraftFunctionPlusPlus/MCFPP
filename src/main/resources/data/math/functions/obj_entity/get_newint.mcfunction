#math:obj_entity/get_newint
scoreboard players operation temp int = @s int
execute as @e[tag=tmp] if score @s reid_temp = temp int run scoreboard players operation temp int = @s id
scoreboard players operation @s int = temp int