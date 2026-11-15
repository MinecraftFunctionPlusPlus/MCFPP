scoreboard players set ma_degrees_sign int 1
scoreboard players set ma_degrees_int0 int 5729
scoreboard players set ma_degrees_int1 int 5779
scoreboard players set ma_degrees_exp int 2
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_sign = ma_degrees_sign int
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_int0 = ma_degrees_int0 int
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_int1 = ma_degrees_int1 int
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_exp = ma_degrees_exp int
execute as 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 run function math.float:hpo/float/_mult
scoreboard players add float_exp int 4
scoreboard players operation theta int = float_int0 int
scoreboard players operation theta int *= 10000 int
scoreboard players operation theta int += float_int1 int
execute if score float_exp int matches 1 run scoreboard players operation theta int /= 10000000 int
execute if score float_exp int matches 2 run scoreboard players operation theta int /= 1000000 int
execute if score float_exp int matches 3 run scoreboard players operation theta int /= 100000 int
execute if score float_exp int matches 4 run scoreboard players operation theta int /= 10000 int
execute if score float_exp int matches 5 run scoreboard players operation theta int /= 1000 int
execute if score float_exp int matches 6 run scoreboard players operation theta int /= 100 int
execute if score float_exp int matches 7 run scoreboard players operation theta int /= 10 int
execute if score float_exp int matches ..0 run scoreboard players set theta int 0
scoreboard players operation theta int %= 3600000 int
scoreboard players operation ma_shift int = float_exp int
scoreboard players remove ma_shift int 8
execute if score ma_shift int matches 1.. run function math.float:hpo/float/angle_shift
scoreboard players operation theta int *= float_sign int
