scoreboard players add upd_aec int 1
execute as @e[tag=upd_aec,type=minecraft:area_effect_cloud] store result entity @s Air short 1 run scoreboard players operation upd_aec int %= 10 int

data modify entity 0-0-0-8593-1 Age set value 0

schedule function math:tick 1t replace