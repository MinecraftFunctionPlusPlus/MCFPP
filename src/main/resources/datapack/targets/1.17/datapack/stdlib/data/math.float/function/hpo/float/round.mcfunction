scoreboard players operation mr_m int = float_int0 int
scoreboard players operation mr_m int *= 10000 int
scoreboard players operation mr_m int += float_int1 int
execute if score float_exp int matches 1..7 run function math.float:hpo/float/round_fraction
execute if score float_exp int matches ..0 run function math.float:hpo/float/round_small
