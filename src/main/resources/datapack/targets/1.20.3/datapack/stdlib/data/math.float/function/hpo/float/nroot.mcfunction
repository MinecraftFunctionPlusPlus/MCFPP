scoreboard players operation mr_input_sign int = float_sign int
scoreboard players operation mr_input_int0 int = float_int0 int
scoreboard players operation mr_input_int1 int = float_int1 int
scoreboard players operation mr_input_exp int = float_exp int
scoreboard players operation mr_odd int = mr_n int
scoreboard players operation mr_odd int %= 2 int
scoreboard players set mr_valid int 1
execute if score mr_n int matches ..1 run scoreboard players set mr_valid int 0
execute if score mr_input_sign int matches -1 if score mr_odd int matches 0 run scoreboard players set mr_valid int 0
execute if score mr_valid int matches 1 unless score mr_input_sign int matches 0 run function math.float:hpo/float/nroot_start
execute if score mr_valid int matches 0 run function math.float:hpo/float/zero
execute if score mr_input_sign int matches 0 run function math.float:hpo/float/zero
