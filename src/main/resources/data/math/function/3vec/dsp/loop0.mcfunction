execute at @s run function math:particle/_dsp

scoreboard players operation sstemp3 int += stemp3 int
scoreboard players operation sstemp4 int += stemp4 int
scoreboard players operation sstemp5 int += stemp5 int
scoreboard players operation sstemp6 int = sstemp3 int
scoreboard players operation sstemp7 int = sstemp4 int
scoreboard players operation sstemp8 int = sstemp5 int
scoreboard players operation sstemp6 int /= 3vec_n int
scoreboard players operation sstemp7 int /= 3vec_n int
scoreboard players operation sstemp8 int /= 3vec_n int
scoreboard players operation sstemp0 int += sstemp6 int
scoreboard players operation sstemp1 int += sstemp7 int
scoreboard players operation sstemp2 int += sstemp8 int
scoreboard players operation sstemp3 int %= 3vec_n int
scoreboard players operation sstemp4 int %= 3vec_n int
scoreboard players operation sstemp5 int %= 3vec_n int

execute store result entity @s Pos[0] double 0.0001 run scoreboard players operation sstemp0 int += stemp0 int
execute store result entity @s Pos[1] double 0.0001 run scoreboard players operation sstemp1 int += stemp1 int
execute store result entity @s Pos[2] double 0.0001 run scoreboard players operation sstemp2 int += stemp2 int
scoreboard players add sloop int 1
execute if score sloop int < 3vec_n int run function math:3vec/dsp/loop0