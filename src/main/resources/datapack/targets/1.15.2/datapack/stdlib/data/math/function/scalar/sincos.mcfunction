# Temporary calculation entity; no persistent entity, tick or forced chunk.
summon armor_stand ~ ~ ~ {Tags:["mcfpp_math_angle"],Invisible:1b,Marker:1b,NoGravity:1b,Invulnerable:1b}
execute as @e[type=minecraft:armor_stand,tag=mcfpp_math_angle,sort=nearest,limit=1] at @s run function math:scalar/sincos_entity
