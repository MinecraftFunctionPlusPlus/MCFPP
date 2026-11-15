scoreboard players operation theta int *= 10 int
scoreboard players operation theta int %= 3600000 int
scoreboard players remove ma_shift int 1
execute if score ma_shift int matches 1.. run function math.float:hpo/float/angle_shift
