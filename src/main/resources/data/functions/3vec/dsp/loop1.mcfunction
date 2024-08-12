execute at @s run function math:particle/_dsp

execute store result entity @s Pos[0] double 0.0001 run scoreboard players operation sstemp0 int += stemp0 int
execute store result entity @s Pos[1] double 0.0001 run scoreboard players operation sstemp1 int += stemp1 int
execute store result entity @s Pos[2] double 0.0001 run scoreboard players operation sstemp2 int += stemp2 int
scoreboard players add sloop int 1
execute if score sloop int < stempn int run function math:3vec/dsp/loop1